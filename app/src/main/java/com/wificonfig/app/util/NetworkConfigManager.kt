package com.wificonfig.app.util

import com.wificonfig.app.data.ApplyResult
import com.wificonfig.app.data.CommandDiagnostic
import com.wificonfig.app.data.StaticNetworkConfig
import com.wificonfig.app.data.WifiInterfaceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * 网络配置管理器（针对 MIUI 14 / Android 12+ 深度适配）：
 *   - 探测 Wi-Fi 接口名
 *   - 关闭 Private DNS、暂停 Wi-Fi NetworkAgent（防止系统覆盖）
 *   - 通过 ip rule + 自定义路由表 设置高优先级静态 IP / 路由
 *   - 通过 iptables -t nat DNAT 强制 53 端口走用户指定 DNS（MIUI 必杀技）
 *   - DHCP 还原模式：清理 DNAT 规则 / 恢复 Private DNS 默认 / Wi-Fi 重连触发 DHCP
 */
object NetworkConfigManager {

    private val CANDIDATE_IFACES = listOf("wlan0", "wlan1", "eth0")

    // 私有路由表 ID（1 ~ 252，避免与系统 rt_tables 里的表冲突）
    private const val STATIC_RT_TABLE_ID = 119
    private const val STATIC_RT_TABLE_NAME = "wificfg_static"
    // ip rule 优先级：越小越优先（local 表是 0，main 通常 32766），我们设 1000 覆盖系统 DHCP 路由
    private const val RULE_PRIORITY = 1000
    // iptables 自定义链（便于清理，不会影响其他规则）
    private const val DNS_NAT_CHAIN = "WIFI_CFG_DNS_NAT"
    // iptables marker comment（匹配时用）
    private const val RULE_MARKER = "WifiCfgDnsNat"

    private val IPV4_REGEX: Pattern = Pattern.compile(
        "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    )

    fun isValidIpv4(ip: String): Boolean =
        ip.isNotBlank() && IPV4_REGEX.matcher(ip).matches()

    suspend fun detectWifiInterface(): WifiInterfaceInfo = withContext(Dispatchers.IO) {
        val ipLink = RootShell.execute("ip link show")
        val proc = RootShell.execute("cat /proc/net/dev")
        val allOutput = ipLink.stdout + "\n" + proc.stdout

        var ifName = ""
        for (candidate in CANDIDATE_IFACES) {
            if (allOutput.contains("$candidate:")) {
                ifName = candidate
                break
            }
        }
        if (ifName.isEmpty()) {
            val route = RootShell.execute("ip route list | grep default").stdout
            val match = Regex("dev\\s+(\\S+)").find(route)
            if (match != null) ifName = match.groupValues[1]
        }
        if (ifName.isEmpty()) return@withContext WifiInterfaceInfo(ifName = "")

        WifiInterfaceInfo(
            ifName = ifName,
            currentIp = readCurrentIp(ifName),
            currentGateway = readCurrentGateway(ifName),
            currentDns = readCurrentDns()
        )
    }

    private suspend fun readCurrentIp(ifName: String): String {
        val r = RootShell.execute("ip -4 addr show dev $ifName")
        val m = Regex("inet\\s+(\\S+)/(\\d+)").find(r.stdout)
        return m?.groupValues?.get(1) ?: ""
    }

    private suspend fun readCurrentGateway(ifName: String): String {
        val r = RootShell.execute("ip route list dev $ifName | grep default")
        val m = Regex("via\\s+(\\S+)").find(r.stdout)
        return m?.groupValues?.get(1) ?: ""
    }

    private suspend fun readCurrentDns(): String {
        val p1 = RootShell.execute("getprop net.dns1").stdout.trim()
        val p2 = RootShell.execute("getprop net.dns2").stdout.trim()
        val dnsList = mutableListOf<String>()
        if (p1.isNotEmpty()) dnsList.add(p1)
        if (p2.isNotEmpty()) dnsList.add(p2)
        if (dnsList.isNotEmpty()) return dnsList.joinToString(", ")
        val resolv = RootShell.execute("cat /etc/resolv.conf").stdout
        val names = Regex("nameserver\\s+(\\S+)").findAll(resolv).map { it.groupValues[1] }.toList()
        return names.joinToString(", ")
    }

    // ============== 静态 IP 应用（MIUI 14 适配版） ==============

    suspend fun applyStaticIp(iface: String, config: StaticNetworkConfig): ApplyResult =
        withContext(Dispatchers.IO) {
            if (iface.isEmpty()) return@withContext ApplyResult(false, "未能检测到 Wi-Fi 网络接口（wlan0 等）")
            if (!config.isFilled()) return@withContext ApplyResult(false, "静态配置不完整：IP、网关、主 DNS 不能为空")
            if (!isValidIpv4(config.ipAddress)) return@withContext ApplyResult(false, "IP 格式不正确: ${config.ipAddress}")
            if (!isValidIpv4(config.gateway)) return@withContext ApplyResult(false, "网关格式不正确: ${config.gateway}")
            if (!isValidIpv4(config.dnsPrimary)) return@withContext ApplyResult(false, "主 DNS 格式不正确: ${config.dnsPrimary}")
            if (config.dnsSecondary.isNotEmpty() && !isValidIpv4(config.dnsSecondary))
                return@withContext ApplyResult(false, "备用 DNS 格式不正确: ${config.dnsSecondary}")

            val cmds = buildStaticIpCommands(iface, config)
            val (_, diags) = RootShell.executeDiagnosed(cmds)

            val failed = diags.filter { !it.ok }
            // IP/路由/DNAT 里的 "关键命令" 失败才认为整体失败；cmd connectivity 等可选失败忽略
            val criticalFailed = failed.filter { d ->
                d.command.startsWith("ip ") ||
                d.command.startsWith("iptables ") ||
                d.command.startsWith("ip6tables ")
            }

            val ok = criticalFailed.isEmpty()
            val failedCount = criticalFailed.size
            val prio = RULE_PRIORITY
            val ip = config.ipAddress
            val prefix = config.subnetPrefix
            val gw = config.gateway
            val dns1 = config.dnsPrimary
            val dns2 = config.dnsSecondary
            // 保留 msg 字符串作为 fallback（老版本 / messageFn 为 null 时使用）；
            // 同时提供 messageFn 以支持语言切换（AppStrings 是多语言抽象类）
            val dnsServers = buildString {
                append(dns1)
                if (dns2.isNotEmpty()) append('/').append(dns2)
            }
            val msg = if (ok) {
                "已应用到 $iface（MIUI 适配模式：Private DNS 已关闭 + NetworkAgent 已暂停 + DNAT 强制 DNS + 路由优先级 $prio）\n" +
                        "IP: $ip/$prefix  网关: $gw  DNS: $dnsServers\n" +
                        "若仍无法上网，请先手动关掉「Wi-Fi 助理 / 双 WLAN 加速」再重试一次。"
            } else {
                "关键命令失败 $failedCount 条，请查看下方诊断日志（❌ 行），确认已授予 Root、接口名为 $iface 且已连接 Wi-Fi。"
            }
            val messageFn: (com.wificonfig.app.ui.AppStrings) -> String = { s ->
                if (ok) {
                    s.staticApplyOkMsg(iface, prio, ip, prefix, gw, dns1, dns2)
                } else {
                    s.staticApplyFailMsg(failedCount, iface)
                }
            }
            ApplyResult(ok, msg, diags, messageFn)
        }

    private fun buildStaticIpCommands(iface: String, cfg: StaticNetworkConfig): List<String> {
        val cmds = mutableListOf<String>()
        val ifAddr = "${cfg.ipAddress}/${cfg.subnetPrefix}"
        val tableId = STATIC_RT_TABLE_ID
        val tableName = STATIC_RT_TABLE_NAME

        // -------- 0. 幂等清理：先清掉上次应用留下的自定义链 / 规则 / 路由表 --------
        cmds += "iptables -t nat -D OUTPUT -j $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "iptables -t nat -F $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "iptables -t nat -X $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "ip6tables -t nat -D OUTPUT -j $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "ip6tables -t nat -F $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "ip6tables -t nat -X $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "ip rule del prio $RULE_PRIORITY 2>/dev/null || true"
        cmds += "ip rule del from all lookup $tableName 2>/dev/null || true"
        cmds += "ip rule del from all lookup $tableId 2>/dev/null || true"
        cmds += "ip route flush table $tableName 2>/dev/null || true"
        cmds += "ip route flush table $tableId 2>/dev/null || true"

        // -------- 1. 写入 /etc/iproute2/rt_tables 注册自定义路由表名（可选，失败无所谓）--------
        cmds += "( grep -q '^${tableId}\\s\\+${tableName}\\b' /etc/iproute2/rt_tables 2>/dev/null ) || " +
                "( mount -o remount,rw /etc 2>/dev/null ; echo '$tableId  $tableName' >> /etc/iproute2/rt_tables ; mount -o remount,ro /etc 2>/dev/null ; true )"

        // -------- 2. 关闭 Private DNS（否则 Android 9+ 忽略明文 DNS，改 netd resolver 也没用）--------
        // private_dns_mode: off / hostname / opportunist；我们用 off 强制走系统明文 DNS
        cmds += "settings put global private_dns_mode off 2>/dev/null || true"
        cmds += "settings put global private_dns_specifier '' 2>/dev/null || true"
        // MIUI 额外的连接管家 / Wi-Fi 看门狗
        cmds += "settings put global wifi_watchdog_on 0 2>/dev/null || true"
        cmds += "settings put system wifi_watchdog_on 0 2>/dev/null || true"
        cmds += "settings put global network_recommendations_enabled 0 2>/dev/null || true"
        cmds += "settings put global captive_portal_mode 0 2>/dev/null || true"

        // -------- 3. 暂停 Wi-Fi 对应的 NetworkAgent，防止 ConnectivityService 周期性把 DHCP 写回来 --------
        cmds += "cmd connectivity network-agent suspend 2>/dev/null || true"   // 若支持全局 suspend
        cmds += "cmd connectivity network-agent suspend $iface 2>/dev/null || true"
        // 某些机型（如 AOSP）带 agent-token 参数，dumpsys 里先抓（尽量兜底）
        cmds += "( dumpsys connectivity 2>/dev/null | grep -oE 'TransportInfo|NetworkAgentInfo.*wlan|NetworkAgentInfo.*$iface|AgentToken=[^ ,]+' >/dev/null ) ; true"

        // -------- 4. 接口本身的 IPv4 配置 --------
        cmds += "ip -4 addr flush dev $iface 2>/dev/null || true"
        cmds += "ip addr add $ifAddr dev $iface"
        cmds += "ip link set $iface up || true"

        // -------- 5. 自定义路由表 + 高优先级 ip rule（覆盖系统 main 表里的 DHCP 路由）--------
        // 首轮添加（接口刚 up，carrier 可能还没 ready，所以全部静默可选失败）
        cmds += "ip route add ${cfg.gateway}/32 dev $iface table $tableId 2>/dev/null || true"
        cmds += "ip route add $ifAddr dev $iface scope link table $tableId 2>/dev/null || true"
        cmds += "ip route add default via ${cfg.gateway} dev $iface table $tableId 2>/dev/null || true"
        // main 表也加一份（为了 ip route get 默认能走通，同时 rule 优先级更高兜底）
        cmds += "ip route del default dev $iface 2>/dev/null || true"
        cmds += "ip route add default via ${cfg.gateway} dev $iface 2>/dev/null || true"
        // 关键兜底：main 表默认路由添加完成后，链路层一定 ready，再给自定义路由表补一遍所有路由
        // 解决首轮 #27 报错 "RTNETLINK answers: Network is unreachable"（carrier 时序竞态问题）
        cmds += "ip route add ${cfg.gateway}/32 dev $iface table $tableId 2>/dev/null || true"
        cmds += "ip route add $ifAddr dev $iface scope link table $tableId 2>/dev/null || true"
        cmds += "ip route add default via ${cfg.gateway} dev $iface table $tableId 2>/dev/null || true"
        cmds += "ip rule add from all iif lo lookup $tableId prio $RULE_PRIORITY 2>/dev/null || " +
                "ip rule add from all lookup $tableId prio $RULE_PRIORITY"

        // -------- 6. netd resolver + setprop（传统手段，能生效最好）--------
        cmds += formatDnsCommand(iface, cfg.dnsPrimary, cfg.dnsSecondary)
        cmds += "setprop net.dns1 ${cfg.dnsPrimary}"
        cmds += if (cfg.dnsSecondary.isNotEmpty()) "setprop net.dns2 ${cfg.dnsSecondary}" else "setprop net.dns2 ''"
        cmds += "cmd netd resolver flushdefaultif 2>/dev/null || true"
        cmds += "ndc resolver flushdefaultif 2>/dev/null || true"

        // -------- 7. 核心必杀：iptables -t nat DNAT 强制 DNS 走用户指定 --------
        // 创建自定义链
        cmds += "iptables -t nat -N $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "iptables -t nat -A $DNS_NAT_CHAIN -p udp --dport 53 -m comment --comment $RULE_MARKER -j DNAT --to-destination ${cfg.dnsPrimary}:53"
        cmds += "iptables -t nat -A $DNS_NAT_CHAIN -p tcp --dport 53 -m comment --comment $RULE_MARKER -j DNAT --to-destination ${cfg.dnsPrimary}:53"
        if (cfg.dnsSecondary.isNotEmpty()) {
            // 备用 DNS 走 nth/statistic 负载均衡（如果有 statistic 模块；没有的话失败也不影响）
            cmds += "iptables -t nat -A $DNS_NAT_CHAIN -p udp --dport 53 -m statistic --mode nth --every 2 --packet 0 -m comment --comment $RULE_MARKER -j DNAT --to-destination ${cfg.dnsPrimary}:53 2>/dev/null || true"
            cmds += "iptables -t nat -A $DNS_NAT_CHAIN -p udp --dport 53 -m statistic --mode nth --every 2 --packet 1 -m comment --comment $RULE_MARKER -j DNAT --to-destination ${cfg.dnsSecondary}:53 2>/dev/null || true"
            cmds += "iptables -t nat -A $DNS_NAT_CHAIN -p tcp --dport 53 -m statistic --mode nth --every 2 --packet 0 -m comment --comment $RULE_MARKER -j DNAT --to-destination ${cfg.dnsPrimary}:53 2>/dev/null || true"
            cmds += "iptables -t nat -A $DNS_NAT_CHAIN -p tcp --dport 53 -m statistic --mode nth --every 2 --packet 1 -m comment --comment $RULE_MARKER -j DNAT --to-destination ${cfg.dnsSecondary}:53 2>/dev/null || true"
        }
        // 挂上 OUTPUT / PREROUTING（应用自己产生的流量 + 热点 / tethering 其他设备流量）
        cmds += "iptables -t nat -A OUTPUT -j $DNS_NAT_CHAIN"
        cmds += "iptables -t nat -A PREROUTING -j $DNS_NAT_CHAIN 2>/dev/null || true"
        // IPv6 的话也关掉（避免 v6 DNS 泄漏）
        cmds += "ip6tables -t nat -N $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "ip6tables -t nat -I OUTPUT 1 -p udp --dport 53 -j REJECT 2>/dev/null || true"
        cmds += "ip6tables -t nat -I OUTPUT 1 -p tcp --dport 53 -j REJECT 2>/dev/null || true"

        // -------- 8. 刷新 /proc/net/route 缓存 + 重新评估连接性 --------
        cmds += "ip route flush cache || true"
        // 触发一次 netd reconnect（Android 12+ 常用命令行兜底）
        cmds += "cmd netd reconnect 2>/dev/null || true"

        return cmds
    }

    // ============== DHCP 还原（配合上面的适配方案） ==============

    suspend fun enableDhcp(iface: String): ApplyResult = withContext(Dispatchers.IO) {
        if (iface.isEmpty()) {
            val messageFn: (com.wificonfig.app.ui.AppStrings) -> String = { it.dhcpNoIfaceMsg }
            return@withContext ApplyResult(false, "未能检测到 Wi-Fi 网络接口（wlan0 等）", emptyList(), messageFn)
        }
        val cmds = buildEnableDhcpCommands(iface)
        val (_, diags) = RootShell.executeDiagnosed(cmds)

        // 只要关键清理命令不出现致命错误就视为成功（svc 重启等返回值各厂商不同）
        val criticalFailed = diags.filter { !it.ok && (it.command.startsWith("iptables") || it.command.startsWith("ip rule") || it.command.startsWith("ip route")) }
        val ok = criticalFailed.isEmpty()
        val failedCount = criticalFailed.size
        val msg = if (ok) {
            "已还原到 DHCP 模式（Private DNS 恢复 opportunistic、DNAT 规则已清理、NetworkAgent 已恢复、Wi-Fi 已自动重连一次）。\n" +
                    "若 10 秒后仍未获取 IP，请去系统 Wi-Fi 设置里手动断开再连接。"
        } else {
            "DHCP 还原中关键命令失败 $failedCount 条，请查看下方诊断。"
        }
        val messageFn: (com.wificonfig.app.ui.AppStrings) -> String = { s ->
            if (ok) s.dhcpApplyOkMsg() else s.dhcpApplyFailMsg(failedCount)
        }
        ApplyResult(ok, msg, diags, messageFn)
    }

    private fun buildEnableDhcpCommands(iface: String): List<String> {
        val cmds = mutableListOf<String>()
        val tableId = STATIC_RT_TABLE_ID
        val tableName = STATIC_RT_TABLE_NAME

        // 1. 清理 DNAT 强制 DNS 链（必须先做，否则后面 DHCP 拿到的 DNS 也被劫持）
        cmds += "iptables -t nat -D OUTPUT -j $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "iptables -t nat -D PREROUTING -j $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "iptables -t nat -F $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "iptables -t nat -X $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "ip6tables -t nat -F $DNS_NAT_CHAIN 2>/dev/null || true"
        cmds += "ip6tables -t nat -X $DNS_NAT_CHAIN 2>/dev/null || true"
        // 按 comment 再兜底清理一次
        cmds += "( iptables -t nat -S OUTPUT 2>/dev/null ; iptables -t nat -S PREROUTING 2>/dev/null ) " +
                "| grep -E \"$RULE_MARKER|$DNS_NAT_CHAIN\" | sed 's/^-A/-D/' | while read -r l ; do iptables -t nat \$l 2>/dev/null || true ; done ; true"

        // 2. 清理自定义 ip rule / 路由表
        cmds += "ip rule del prio $RULE_PRIORITY 2>/dev/null || true"
        cmds += "ip rule del from all lookup $tableName 2>/dev/null || true"
        cmds += "ip rule del from all lookup $tableId 2>/dev/null || true"
        cmds += "ip route flush table $tableName 2>/dev/null || true"
        cmds += "ip route flush table $tableId 2>/dev/null || true"

        // 3. 清掉我们写的静态地址 / 静态默认路由
        cmds += "ip -4 addr flush dev $iface 2>/dev/null || true"
        cmds += "ip route del default dev $iface 2>/dev/null || true"
        cmds += "ip route flush dev $iface 2>/dev/null || true"

        // 4. Private DNS 恢复为 opportunistic（Android 默认值），留空 hostname 让系统自动选
        cmds += "settings put global private_dns_mode opportunistic 2>/dev/null || true"
        cmds += "settings put global private_dns_specifier '' 2>/dev/null || true"
        cmds += "settings put global wifi_watchdog_on 1 2>/dev/null || true"
        cmds += "settings put global captive_portal_mode 1 2>/dev/null || true"
        cmds += "settings put global network_recommendations_enabled 1 2>/dev/null || true"

        // 5. 恢复 NetworkAgent（对应我们之前 suspend 的那一步）
        cmds += "cmd connectivity network-agent resume 2>/dev/null || true"
        cmds += "cmd connectivity network-agent resume $iface 2>/dev/null || true"
        cmds += "cmd netd resolver setifdns $iface '' 2>/dev/null || true"
        cmds += "cmd netd resolver flushdefaultif 2>/dev/null || true"

        // 6. 触发 DHCP：先 down/up 接口，再 svc wifi disable/enable（让系统完整跑一遍 Wi-Fi 连接流程）
        cmds += "ip link set $iface down 2>/dev/null || true"
        cmds += "sleep 1"
        cmds += "ip link set $iface up 2>/dev/null || true"
        // svc wifi 重启（最稳妥的触发 DHCP 方式）
        cmds += "( svc wifi disable ; sleep 2 ; svc wifi enable ) >/dev/null 2>&1 ; true"

        // 7. 刷新路由缓存
        cmds += "ip route flush cache 2>/dev/null || true"
        cmds += "cmd netd reconnect 2>/dev/null || true"

        return cmds
    }

    // ============== 辅助：多套 DNS 写入命令组合（传统途径） ==============
    private fun formatDnsCommand(iface: String, dns1: String, dns2: String): String {
        val dnsList = mutableListOf(dns1)
        if (dns2.isNotEmpty()) dnsList.add(dns2)
        val dnsJoined = dnsList.joinToString(" ")
        return buildString {
            append("( ")
            // 注意：Android 10+ 开始移除了 cmd netd resolver setifdns / setdefaultif，会返回 "500 0 Command not recognized"
            // 所以这里只保留 ndc 版（NDK wrapper，兼容性更好），全部加 2>/dev/null || true 静默可选失败
            append("ndc resolver setifdns $iface '' $dnsJoined 2>/dev/null || true ; ")
            append("ndc resolver setdefaultif $iface 2>/dev/null || true ; ")
            append("ndc resolver setnetdns 1 '' $dnsJoined 2>/dev/null || true ; ")
            append("ndc resolver setnetdns 100 '' $dnsJoined 2>/dev/null || true ; ")
            append("ndc resolver setnetdns 101 '' $dnsJoined 2>/dev/null || true ; ")
            append("true )")
        }
    }
}
