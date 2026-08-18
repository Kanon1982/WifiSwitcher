package com.wificonfig.app.ui

import java.util.Locale

/**
 * 语言选项（用户在 App 内的选择）
 * system = 跟随系统（中文系统→中文，其他系统→英文）
 */
enum class LanguageOption(val storageValue: String) {
    SYSTEM("system"),
    ZH("zh"),
    EN("en");

    companion object {
        fun fromStorage(raw: String?): LanguageOption =
            entries.firstOrNull { it.storageValue == raw } ?: SYSTEM
    }
}

/**
 * 用户可见的字符串集合（全量覆盖 UI 文本 + ViewModel 消息）
 * 通过 ZhStrings / EnStrings 两种实现切换语言。
 *
 * 命名规则：
 *   top*         顶栏
 *   card*        状态卡片文案
 *   mode*        模式选择（自动 / 手动）
 *   dhcp*        DHCP 模式提示
 *   form*        静态表单标题
 *   sec*         表单分组标题 + 提示
 *   label*       输入框标签
 *   hint*        输入框 placeholder / 分组 hint
 *   btn*         通用按钮文字
 *   banner*      状态横幅默认文案
 *   diag*        诊断日志卡片文案
 *   preset*      预设相关（标题/按钮/对话框/Snackbar）
 *   dlg*         对话框标题/描述
 *   lang*        语言选择对话框文案 + Snackbar
 *   status*      ViewModel 使用的 statusMessage 文案（状态条）
 *   snackbar*    ViewModel 使用的 Snackbar 文案（toast 形式）
 *   defaultPresetName(n)  : 新建预设默认名
 *   msgSaved     : OperationStatus.Success 内 message
 */
abstract class AppStrings(
    val tag: String,
    val locale: Locale
) {
    // ==================== 顶栏 ====================
    abstract val topTitle: String
    abstract val topSubtitle: String

    // ==================== 语言相关（顶栏按钮 + 对话框 + Snackbar）====================
    abstract val langButtonDesc: String
    abstract val langTitle: String
    abstract val langOptSystem: String
    abstract val langOptSystemSub: String
    abstract val langOptZh: String
    abstract val langOptZhSub: String
    abstract val langOptEn: String
    abstract val langOptEnSub: String
    abstract val langSnackbarFollowSystem: String
    abstract val langSnackbarZh: String
    abstract val langSnackbarEn: String

    // ==================== 当前状态卡片 ====================
    abstract val cardRootChecking: String
    abstract val cardRootReady: String
    abstract val cardRootReadySub: String
    abstract val cardRootNotReady: String
    abstract val cardRootNotReadySub: String
    abstract val btnRefresh: String
    abstract val cardIfaceNotFound: String
    abstract val cardIfaceConnected: (String) -> String
    abstract val labelIp: String
    abstract val labelGateway: String
    abstract val labelDns: String
    abstract val cardIfaceHint: String

    // ==================== 模式选择 ====================
    abstract val modeAutoTitle: String
    abstract val modeAutoSubEn: String
    abstract val modeAutoDesc: String
    abstract val modeManualTitle: String
    abstract val modeManualSubEn: String
    abstract val modeManualDesc: String
    abstract val cardCurrentSelected: String

    // ==================== DHCP 绿色提示卡 ====================
    abstract val dhcpCardTitle: String
    abstract val dhcpCardDesc: String

    // ==================== 静态表单 ====================
    abstract val formTitle: String
    abstract val formSubtitle: String

    abstract val secIpTitle: String
    abstract val secIpHint: String
    abstract val hintIp: String
    abstract val labelPrefix: String
    abstract val hintPrefix: String

    abstract val secGwTitle: String
    abstract val secGwHint: String
    abstract val hintGateway: String

    abstract val secDnsTitle: String
    abstract val secDnsHint: String
    abstract val labelDnsPrimary: String
    abstract val hintDnsPrimary: String
    abstract val labelDnsSecondary: String
    abstract val hintDnsSecondary: String

    // ==================== 底部主按钮 ====================
    abstract val btnSave: String
    abstract val btnApplyStatic: String
    abstract val btnApplyDhcp: String
    abstract val btnBusy: String

    // ==================== 通用按钮 ====================
    abstract val btnCancel: String
    abstract val btnConfirm: String
    abstract val btnDeleteConfirm: String
    abstract val btnClearAll: String

    // ==================== 横幅默认文案 ====================
    abstract val bannerProcessing: String
    abstract val bannerSuccess: String
    abstract val bannerFailed: String

    // ==================== 诊断日志卡片 ====================
    abstract val diagAllOk: String
    abstract val diagAllOkSub: String
    abstract val diagSomeFailed: (Int, Int) -> String
    abstract val diagFailedSub: String
    abstract val diagExpand: String
    abstract val diagCollapse: String

    // ==================== 预设方案 UI ====================
    abstract val presetTitle: (Int, Int) -> String
    abstract val presetSubtitle: (Int) -> String
    abstract val presetClearAll: String
    abstract val presetSaveAs: String
    abstract val presetFooterHint: String
    abstract val presetEmptyHint: String
    abstract val presetLoadedTag: String
    abstract val presetRename: String
    abstract val presetDelete: String

    // ==================== 预设对话框 ====================
    abstract val dlgCreateTitle: String
    abstract val dlgCreateSub: String
    abstract val dlgCreateLabel: String
    abstract val dlgCreatePlaceholder: String
    abstract val dlgRenameTitle: String
    abstract val dlgRenameLabel: String
    abstract val dlgDeleteTitle: String
    abstract val dlgDeleteMsg: (String) -> String
    abstract val dlgClearTitle: String
    abstract val dlgClearMsg: (Int) -> String

    // ============================================================
    //  ViewModel 用：Status 消息 / Snackbar / 错误（多语言）
    // ============================================================
    abstract val statusIdleReady: String
    abstract val statusCheckingRoot: String
    abstract val statusRootGranted: String
    abstract val statusRootDenied: String
    abstract val statusDetectingIface: String
    abstract val statusNoIface: String
    abstract val statusReadyWithIface: (String, String) -> String
    abstract val msgSaved: String
    abstract val statusSavedLocal: String

    abstract val snackbarRootNotGranted: String
    abstract val snackbarConfigSaved: String
    abstract val snackbarApplyNeedRootFirst: String
    abstract val snackbarStaticIncomplete: String
    abstract val statusApplyingStatic: String
    abstract val statusApplyStaticTimeout: String
    abstract val statusApplyStaticException: (String, String) -> String
    abstract val statusStaticAppliedOk: String
    abstract val statusApplyingDhcp: String
    abstract val statusDhcpTimeout: String
    abstract val statusDhcpException: (String, String) -> String
    abstract val statusDhcpAppliedOk: String
    abstract val snackbarSystemSettingsNotice: String

    // --- 预设 snackbar ---
    abstract val snackbarPresetFillFirst: String
    abstract val snackbarPresetMaxReached: (Int) -> String
    abstract val snackbarPresetIncomplete: String
    abstract val defaultPresetName: (Int) -> String
    abstract val snackbarPresetDupExists: (String) -> String
    abstract val snackbarPresetSaved: (String, Int, Int) -> String
    abstract val snackbarPresetSaveFail: (Int) -> String
    abstract val snackbarPresetRenameEmpty: String
    abstract val snackbarPresetRenamed: (String) -> String
    abstract val snackbarPresetDeleted: String
    abstract val snackbarPresetAllCleared: String
    abstract val snackbarPresetLoaded: (String) -> String

    // ============ 应用结果横幅长消息（底部三行 / 两行详细描述，跟随语言切换） ============
    // params: iface, rulePriority, ip, prefix, gateway, dnsPrimary, dnsSecondary（dnsSecondary 可能为空字符串）
    abstract val staticApplyOkMsg: (String, Int, String, Int, String, String, String) -> String
    // params: failCount, iface
    abstract val staticApplyFailMsg: (Int, String) -> String
    abstract val dhcpApplyOkMsg: () -> String
    // params: failCount
    abstract val dhcpApplyFailMsg: (Int) -> String
    abstract val dhcpNoIfaceMsg: String
}

// ============================================================
//  中文实现
// ============================================================
class ZhStrings : AppStrings("zh", Locale.SIMPLIFIED_CHINESE) {

    // ============ 顶栏 ============
    override val topTitle: String = "Wi-Fi 配置管理器"
    override val topSubtitle: String = "一键改 Wi-Fi 的 IP / DNS · 需要 Root 权限"

    // ============ 语言 ============
    override val langButtonDesc: String = "切换显示语言"
    override val langTitle: String = "选择显示语言"
    override val langOptSystem: String = "跟随系统（推荐）"
    override val langOptSystemSub: String = "中文系统 → 中文；其他语言 → 英文"
    override val langOptZh: String = "简体中文"
    override val langOptZhSub: String = "强制中文显示"
    override val langOptEn: String = "English"
    override val langOptEnSub: String = "Force English"
    override val langSnackbarFollowSystem: String = "已切换为：跟随系统语言"
    override val langSnackbarZh: String = "已切换为：简体中文"
    override val langSnackbarEn: String = "已切换为：English"

    // ============ 状态卡片 ============
    override val cardRootChecking: String = "正在检查 Root 权限…"
    override val cardRootReady: String = "Root 权限已就绪 ✓"
    override val cardRootReadySub: String = "可以放心修改网络设置啦。"
    override val cardRootNotReady: String = "⚠️ 还没有 Root 权限"
    override val cardRootNotReadySub: String = "请用 Magisk 等工具给本应用授权，否则改网络不会生效。"
    override val btnRefresh: String = "刷新网络状态"
    override val cardIfaceNotFound: String = "未检测到 Wi-Fi"
    override val cardIfaceConnected: (String) -> String = { iface -> "当前连接：$iface" }
    override val labelIp: String = "IP 地址"
    override val labelGateway: String = "网关"
    override val labelDns: String = "DNS"
    override val cardIfaceHint: String = "👉 请先连接 Wi-Fi，再点右上角 ↻ 刷新一下。"

    // ============ 模式选择 ============
    override val modeAutoTitle: String = "自动模式"
    override val modeAutoSubEn: String = "DHCP · 推荐"
    override val modeAutoDesc: String = "路由器自动帮你配好，最省心"
    override val modeManualTitle: String = "手动模式"
    override val modeManualSubEn: String = "静态 IP"
    override val modeManualDesc: String = "自己填 IP / 网关 / DNS"
    override val cardCurrentSelected: String = "当前选择"

    // ============ DHCP 提示卡 ============
    override val dhcpCardTitle: String = "选择了「自动模式」"
    override val dhcpCardDesc: String = "点下面绿色大按钮，就能一键切回路由器自动分配的网络，什么都不用填。"

    // ============ 静态表单 ============
    override val formTitle: String = "填写 IP / 网关 / DNS"
    override val formSubtitle: String = "带 * 的项必填；不会填可以先切到上面的「自动模式」"

    override val secIpTitle: String = "① IP 地址"
    override val secIpHint: String = "前缀一般填 24（= 子网掩码 255.255.255.0）"
    override val hintIp: String = "例: 192.168.1.100"
    override val labelPrefix: String = "前缀"
    override val hintPrefix: String = "24"

    override val secGwTitle: String = "② 网关 Gateway"
    override val secGwHint: String = "一般就是路由器管理页 IP"
    override val hintGateway: String = "例: 192.168.1.1  或  192.168.0.1  或  192.168.31.1"

    override val secDnsTitle: String = "③ DNS"
    override val secDnsHint: String = "主 DNS 必需；备用不填也行"
    override val labelDnsPrimary: String = "主 DNS"
    override val hintDnsPrimary: String = "国内推荐 223.5.5.5(阿里) / 114.114.114.114  国外 8.8.8.8"
    override val labelDnsSecondary: String = "备用 DNS（可选）"
    override val hintDnsSecondary: String = "例: 223.6.6.6 / 8.8.4.4"

    // ============ 底部主按钮 ============
    override val btnSave: String = "保存设置"
    override val btnApplyStatic: String = "立即应用设置"
    override val btnApplyDhcp: String = "一键切回自动模式"
    override val btnBusy: String = "处理中，请稍等…"

    // ============ 通用按钮 ============
    override val btnCancel: String = "取消"
    override val btnConfirm: String = "确定"
    override val btnDeleteConfirm: String = "确定删除"
    override val btnClearAll: String = "全部清空"

    // ============ 横幅默认文案 ============
    override val bannerProcessing: String = "处理中…"
    override val bannerSuccess: String = "成功 ✓"
    override val bannerFailed: String = "失败了"

    // ============ 诊断日志 ============
    override val diagAllOk: String = "命令执行全部成功 ✓"
    override val diagAllOkSub: String = "网络改好了～如果没有生效，可以开关一下飞行模式再看。"
    override val diagSomeFailed: (Int, Int) -> String = { fail, total -> "有 $fail 条命令失败了（共 $total 条）" }
    override val diagFailedSub: String = "可以点开下面的详细日志，把截图发给开发者排查问题。"
    override val diagExpand: String = "展开详情"
    override val diagCollapse: String = "收起"

    // ============ 预设方案 UI ============
    override val presetTitle: (Int, Int) -> String = { cur, max -> "我的预设方案  $cur/$max" }
    override val presetSubtitle: (Int) -> String = { max -> "保存多套常用配置，一键切换。最多 $max 套。" }
    override val presetClearAll: String = "清空全部"
    override val presetSaveAs: String = "另存为预设"
    override val presetFooterHint: String = "👆 点一下某一行 = 自动填入上面表单；✏️铅笔 = 改名；🗑️垃圾桶 = 删除"
    override val presetEmptyHint: String = "还没有预设 —— 填好上面 5 个字段后，点「另存为预设」就能保存啦。"
    override val presetLoadedTag: String = "✓ 已载入到表单"
    override val presetRename: String = "重命名"
    override val presetDelete: String = "删除"

    // ============ 预设对话框 ============
    override val dlgCreateTitle: String = "另存为预设"
    override val dlgCreateSub: String = "给这套配置起个名字（24 字以内）："
    override val dlgCreateLabel: String = "预设名称"
    override val dlgCreatePlaceholder: String = "如：家里主路由 2.4G / 公司 Wi-Fi"
    override val dlgRenameTitle: String = "重命名预设"
    override val dlgRenameLabel: String = "新名称（24 字以内）"
    override val dlgDeleteTitle: String = "删除预设"
    override val dlgDeleteMsg: (String) -> String = { name -> "确定删除「$name」吗？删了就找不回来啦。" }
    override val dlgClearTitle: String = "清空全部预设"
    override val dlgClearMsg: (Int) -> String = { n -> "真的要删除全部 $n 套预设吗？此操作不可恢复哦。" }

    // ============ ViewModel: Status ============
    override val statusIdleReady: String = "就绪"
    override val statusCheckingRoot: String = "正在检查 Root 权限…"
    override val statusRootGranted: String = "Root 权限已获取"
    override val statusRootDenied: String = "未获取 Root 权限，无法修改网络配置"
    override val statusDetectingIface: String = "正在探测 Wi-Fi 接口…"
    override val statusNoIface: String = "未检测到网络接口。请确保 Wi-Fi 已连接。"
    override val statusReadyWithIface: (String, String) -> String =
        { iface, ip -> "就绪：接口 $iface（当前 IP: ${ip.ifBlank { "-" }}）" }
    override val msgSaved: String = "已保存"
    override val statusSavedLocal: String = "配置已保存到本地"

    // ============ ViewModel: Snackbar ============
    override val snackbarRootNotGranted: String =
        "未获取 Root 权限。请确认设备已 Root，并在弹窗中授予本应用权限。"
    override val snackbarConfigSaved: String = "配置已保存"
    override val snackbarApplyNeedRootFirst: String = "请先授予 Root 权限后再应用配置"
    override val snackbarStaticIncomplete: String = "静态配置不完整，请填写 IP、网关与主 DNS"
    override val statusApplyingStatic: String =
        "正在应用静态 IP 配置…（最长等待 15s / 单命令超时 3~5s）"
    override val statusApplyStaticTimeout: String =
        "应用静态 IP 超时（超过 15 秒）。请展开下方诊断日志查看哪一条命令卡住了，截图发给作者。" +
                "已做单命令级超时兜底：若你仍能看到下面的诊断条目，则说明某些命令累计超过了总时长。"
    override val statusApplyStaticException: (String, String) -> String =
        { clazz, msg -> "应用静态 IP 异常：$clazz: $msg" }
    override val statusStaticAppliedOk: String = "静态 IP 配置应用成功"
    override val statusApplyingDhcp: String =
        "正在切换到 DHCP 模式…（最长等待 20s，因为会自动重启 Wi-Fi）"
    override val statusDhcpTimeout: String =
        "切换 DHCP 超时（超过 20 秒）。通常是因为 `svc wifi disable; sleep 2; svc wifi enable` " +
                "被 MIUI 权限拦截。你可以手动关 Wi-Fi 再开模拟 DHCP 重连；或者展开下方诊断定位具体卡住的命令。"
    override val statusDhcpException: (String, String) -> String =
        { clazz, msg -> "切换 DHCP 异常：$clazz: $msg" }
    override val statusDhcpAppliedOk: String = "已切换到 DHCP 模式，等待自动获取…"
    override val snackbarSystemSettingsNotice: String =
        "💡 小提醒：系统设置里的 Wi-Fi 页面显示的 IP/网关/DNS 可能暂时没变化，" +
                "但安卓底层的网络设置其实已经改好并生效啦 ✅。" +
                "要是想让系统设置页面也同步显示，只需关闭再打开 Wi-Fi，重连一次就好。"

    // --- 预设 snackbar ---
    override val snackbarPresetFillFirst: String = "请先把上方 5 个字段填完整，再另存为预设。"
    override val snackbarPresetMaxReached: (Int) -> String =
        { max -> "已达上限 $max 套预设。请先删除不常用的。" }
    override val snackbarPresetIncomplete: String = "表单内容不完整，无法另存为预设。"
    override val defaultPresetName: (Int) -> String = { n -> "预设 $n" }
    override val snackbarPresetDupExists: (String) -> String =
        { name -> "内容相同且同名的预设已存在（「$name」），无需重复保存。" }
    override val snackbarPresetSaved: (String, Int, Int) -> String =
        { name, cur, max -> "已保存预设「$name」（$cur/$max）" }
    override val snackbarPresetSaveFail: (Int) -> String =
        { max -> "保存失败：已达 $max 套上限，或 ID 冲突。" }
    override val snackbarPresetRenameEmpty: String = "新名称不能为空。"
    override val snackbarPresetRenamed: (String) -> String = { name -> "预设已重命名为「$name」" }
    override val snackbarPresetDeleted: String = "预设已删除"
    override val snackbarPresetAllCleared: String = "已清空全部预设"
    override val snackbarPresetLoaded: (String) -> String =
        { name -> "已载入预设「$name」，已自动切到静态模式。" }

    // ============ 应用结果横幅长消息 ============
    override val staticApplyOkMsg: (String, Int, String, Int, String, String, String) -> String =
        { iface, prio, ip, prefix, gw, dns1, dns2 ->
            val dns = buildString { append(dns1) ; if (dns2.isNotEmpty()) append('/').append(dns2) }
            "已应用到 $iface（MIUI 适配模式：Private DNS 已关闭 + NetworkAgent 已暂停 + DNAT 强制 DNS + 路由优先级 $prio）\n" +
                    "IP: $ip/$prefix  网关: $gw  DNS: $dns\n" +
                    "若仍无法上网，请先手动关掉「Wi-Fi 助理 / 双 WLAN 加速」再重试一次。"
        }
    override val staticApplyFailMsg: (Int, String) -> String =
        { cnt, iface ->
            "关键命令失败 $cnt 条，请查看下方诊断日志（❌ 行），确认已授予 Root、接口名为 $iface 且已连接 Wi-Fi。"
        }
    override val dhcpApplyOkMsg: () -> String =
        {
            "已还原到 DHCP 模式（Private DNS 恢复 opportunistic、DNAT 规则已清理、NetworkAgent 已恢复、Wi-Fi 已自动重连一次）。\n" +
                    "若 10 秒后仍未获取 IP，请去系统 Wi-Fi 设置里手动断开再连接。"
        }
    override val dhcpApplyFailMsg: (Int) -> String =
        { cnt -> "DHCP 还原中关键命令失败 $cnt 条，请查看下方诊断。" }
    override val dhcpNoIfaceMsg: String = "未能检测到 Wi-Fi 网络接口（wlan0 等）"
}

// ============================================================
//  英文实现
// ============================================================
class EnStrings : AppStrings("en", Locale.ENGLISH) {

    // ============ 顶栏 ============
    override val topTitle: String = "Wi-Fi Switcher"
    override val topSubtitle: String = "One-tap Wi-Fi IP / DNS changer · Root required"

    // ============ 语言 ============
    override val langButtonDesc: String = "Switch display language"
    override val langTitle: String = "Display Language"
    override val langOptSystem: String = "Follow System (Recommended)"
    override val langOptSystemSub: String = "Chinese OS → Chinese; all others → English"
    override val langOptZh: String = "简体中文"
    override val langOptZhSub: String = "Force Simplified Chinese"
    override val langOptEn: String = "English"
    override val langOptEnSub: String = "Force English"
    override val langSnackbarFollowSystem: String = "Switched to: Follow System"
    override val langSnackbarZh: String = "Switched to: 简体中文"
    override val langSnackbarEn: String = "Switched to: English"

    // ============ 状态卡片 ============
    override val cardRootChecking: String = "Checking Root permission…"
    override val cardRootReady: String = "Root permission ready ✓"
    override val cardRootReadySub: String = "You can safely modify network settings now."
    override val cardRootNotReady: String = "⚠️ No Root permission yet"
    override val cardRootNotReadySub: String = "Grant SuperSU via Magisk etc., otherwise changes won’t take effect."
    override val btnRefresh: String = "Refresh network status"
    override val cardIfaceNotFound: String = "No Wi-Fi detected"
    override val cardIfaceConnected: (String) -> String = { iface -> "Connected: $iface" }
    override val labelIp: String = "IP"
    override val labelGateway: String = "Gateway"
    override val labelDns: String = "DNS"
    override val cardIfaceHint: String = "👉 Connect to Wi-Fi first, then tap ↻ at top-right to refresh."

    // ============ 模式选择 ============
    override val modeAutoTitle: String = "Auto Mode"
    override val modeAutoSubEn: String = "DHCP · Recommended"
    override val modeAutoDesc: String = "Router assigns everything automatically"
    override val modeManualTitle: String = "Manual Mode"
    override val modeManualSubEn: String = "Static IP"
    override val modeManualDesc: String = "Fill in IP / Gateway / DNS yourself"
    override val cardCurrentSelected: String = "Currently selected"

    // ============ DHCP 提示卡 ============
    override val dhcpCardTitle: String = "You chose \"Auto Mode\""
    override val dhcpCardDesc: String = "Nothing to fill. Just tap the big green button below to switch back to DHCP auto-assign."

    // ============ 静态表单 ============
    override val formTitle: String = "Fill in IP / Gateway / DNS"
    override val formSubtitle: String = "Fields with * are required; otherwise switch to \"Auto Mode\" above."

    override val secIpTitle: String = "① IP Address"
    override val secIpHint: String = "Prefix is usually 24 (= netmask 255.255.255.0)"
    override val hintIp: String = "e.g. 192.168.1.100"
    override val labelPrefix: String = "Prefix"
    override val hintPrefix: String = "24"

    override val secGwTitle: String = "② Gateway"
    override val secGwHint: String = "Usually the router admin page IP"
    override val hintGateway: String = "e.g. 192.168.1.1 / 192.168.0.1 / 192.168.31.1"

    override val secDnsTitle: String = "③ DNS"
    override val secDnsHint: String = "Primary DNS required; secondary optional"
    override val labelDnsPrimary: String = "Primary DNS"
    override val hintDnsPrimary: String = "Recommended: 223.5.5.5 / 114.114.114.114 (CN) · 8.8.8.8 / 1.1.1.1 (Global)"
    override val labelDnsSecondary: String = "Secondary DNS (optional)"
    override val hintDnsSecondary: String = "e.g. 223.6.6.6 / 8.8.4.4"

    // ============ 底部主按钮 ============
    override val btnSave: String = "Save"
    override val btnApplyStatic: String = "Apply settings now"
    override val btnApplyDhcp: String = "One-tap switch to Auto"
    override val btnBusy: String = "Processing…"

    // ============ 通用按钮 ============
    override val btnCancel: String = "Cancel"
    override val btnConfirm: String = "OK"
    override val btnDeleteConfirm: String = "Delete"
    override val btnClearAll: String = "Delete all"

    // ============ 横幅默认文案 ============
    override val bannerProcessing: String = "Processing…"
    override val bannerSuccess: String = "Success ✓"
    override val bannerFailed: String = "Failed"

    // ============ 诊断日志 ============
    override val diagAllOk: String = "All commands succeeded ✓"
    override val diagAllOkSub: String = "Network is updated. If not effective yet, toggle Airplane Mode on then off."
    override val diagSomeFailed: (Int, Int) -> String =
        { fail, total -> "$fail command(s) failed out of $total" }
    override val diagFailedSub: String = "Expand details below and send a screenshot to the developer for troubleshooting."
    override val diagExpand: String = "Expand"
    override val diagCollapse: String = "Collapse"

    // ============ 预设方案 UI ============
    override val presetTitle: (Int, Int) -> String = { cur, max -> "My presets  $cur/$max" }
    override val presetSubtitle: (Int) -> String = { max -> "Save configs for one-tap switching. Up to $max." }
    override val presetClearAll: String = "Clear all"
    override val presetSaveAs: String = "Save as preset"
    override val presetFooterHint: String = "👆 Tap a row = auto-fill form; ✏️ pencil = rename; 🗑️ trash = delete"
    override val presetEmptyHint: String = "No presets yet — fill in the 5 fields above, then tap \"Save as preset\"."
    override val presetLoadedTag: String = "✓ Loaded into form"
    override val presetRename: String = "Rename"
    override val presetDelete: String = "Delete"

    // ============ 预设对话框 ============
    override val dlgCreateTitle: String = "Save as preset"
    override val dlgCreateSub: String = "Give this config a name (max 24 chars):"
    override val dlgCreateLabel: String = "Preset name"
    override val dlgCreatePlaceholder: String = "e.g. Home 5G / Office Wi-Fi"
    override val dlgRenameTitle: String = "Rename preset"
    override val dlgRenameLabel: String = "New name (max 24 chars)"
    override val dlgDeleteTitle: String = "Delete preset"
    override val dlgDeleteMsg: (String) -> String = { name -> "Delete \"$name\"? This cannot be undone." }
    override val dlgClearTitle: String = "Clear all presets"
    override val dlgClearMsg: (Int) -> String = { n -> "Really delete ALL $n presets? This cannot be undone." }

    // ============ ViewModel: Status ============
    override val statusIdleReady: String = "Ready"
    override val statusCheckingRoot: String = "Checking Root permission…"
    override val statusRootGranted: String = "Root permission granted"
    override val statusRootDenied: String = "No Root permission — cannot modify network config"
    override val statusDetectingIface: String = "Detecting Wi-Fi interface…"
    override val statusNoIface: String = "No network interface detected. Make sure Wi-Fi is connected."
    override val statusReadyWithIface: (String, String) -> String =
        { iface, ip -> "Ready: interface $iface (current IP: ${ip.ifBlank { "-" }})" }
    override val msgSaved: String = "Saved"
    override val statusSavedLocal: String = "Config saved locally"

    // ============ ViewModel: Snackbar ============
    override val snackbarRootNotGranted: String =
        "Root permission not granted. Please root your device and grant this app SuperSU in the popup."
    override val snackbarConfigSaved: String = "Config saved"
    override val snackbarApplyNeedRootFirst: String = "Grant Root permission first, then apply config."
    override val snackbarStaticIncomplete: String = "Incomplete static config. Fill in IP, Gateway and Primary DNS."
    override val statusApplyingStatic: String =
        "Applying static IP config… (up to 15s total; per-command timeout 3~5s)"
    override val statusApplyStaticTimeout: String =
        "Applying static IP timed out (>15 seconds). Expand diagnostics below to see which command " +
                "hung and send a screenshot to the developer. Per-command timeouts are in effect — if you can still see diagnostic " +
                "entries, it means their cumulative duration exceeded the global limit."
    override val statusApplyStaticException: (String, String) -> String =
        { clazz, msg -> "Applying static IP exception: $clazz: $msg" }
    override val statusStaticAppliedOk: String = "Static IP config applied successfully"
    override val statusApplyingDhcp: String =
        "Switching to DHCP mode… (up to 20s; Wi-Fi will be toggled automatically)"
    override val statusDhcpTimeout: String =
        "Switching to DHCP timed out (>20 seconds). This is usually because MIUI blocked " +
                "`svc wifi disable; sleep 2; svc wifi enable`. You can manually toggle Wi-Fi off/on to simulate a DHCP reconnect; " +
                "or expand diagnostics to locate the stuck command."
    override val statusDhcpException: (String, String) -> String =
        { clazz, msg -> "DHCP switch exception: $clazz: $msg" }
    override val statusDhcpAppliedOk: String = "Switched to DHCP mode, waiting for auto-acquisition…"
    override val snackbarSystemSettingsNotice: String =
        "💡 Tip: the IP/Gateway/DNS shown in System Settings → Wi-Fi page might not refresh immediately, " +
                "but the low-level Android network stack has already been updated and is effective ✅. " +
                "To sync the System Settings UI too, simply toggle Wi-Fi off and back on."

    // --- 预设 snackbar ---
    override val snackbarPresetFillFirst: String = "Fill in all 5 fields above first, then save as a preset."
    override val snackbarPresetMaxReached: (Int) -> String =
        { max -> "Maximum $max presets reached. Delete unused ones first." }
    override val snackbarPresetIncomplete: String = "Incomplete form — cannot save as preset."
    override val defaultPresetName: (Int) -> String = { n -> "Preset $n" }
    override val snackbarPresetDupExists: (String) -> String =
        { name -> "Identical preset with name \"$name\" already exists, skipping." }
    override val snackbarPresetSaved: (String, Int, Int) -> String =
        { name, cur, max -> "Preset \"$name\" saved ($cur/$max)" }
    override val snackbarPresetSaveFail: (Int) -> String =
        { max -> "Save failed: either $max preset limit reached, or there was an ID collision." }
    override val snackbarPresetRenameEmpty: String = "New name cannot be empty."
    override val snackbarPresetRenamed: (String) -> String = { name -> "Preset renamed to \"$name\"" }
    override val snackbarPresetDeleted: String = "Preset deleted"
    override val snackbarPresetAllCleared: String = "All presets cleared"
    override val snackbarPresetLoaded: (String) -> String =
        { name -> "Loaded preset \"$name\"; auto-switched to Static mode." }

    // ============ 应用结果横幅长消息 ============
    override val staticApplyOkMsg: (String, Int, String, Int, String, String, String) -> String =
        { iface, prio, ip, prefix, gw, dns1, dns2 ->
            val dns = buildString { append(dns1) ; if (dns2.isNotEmpty()) append('/').append(dns2) }
            "Applied to $iface (MIUI-compat mode: Private DNS off + NetworkAgent suspended + DNAT forced DNS + route priority $prio)\n" +
                    "IP: $ip/$prefix  Gateway: $gw  DNS: $dns\n" +
                    "If still no Internet, please turn off \"Wi-Fi Assistant / Dual WLAN acceleration\" in System Settings then retry."
        }
    override val staticApplyFailMsg: (Int, String) -> String =
        { cnt, iface ->
            "$cnt critical command(s) failed. See diagnostics below (❌ rows). Ensure Root is granted, interface name is $iface, and Wi-Fi is connected."
        }
    override val dhcpApplyOkMsg: () -> String =
        {
            "Reverted to DHCP mode (Private DNS reset to opportunistic, DNAT rules cleaned, NetworkAgent resumed, Wi-Fi auto-reconnected once).\n" +
                    "If still no IP after 10 seconds, please go to System Wi-Fi Settings and manually disconnect then reconnect."
        }
    override val dhcpApplyFailMsg: (Int) -> String =
        { cnt -> "$cnt critical command(s) failed during DHCP revert. See diagnostics below." }
    override val dhcpNoIfaceMsg: String = "Could not detect Wi-Fi network interface (e.g. wlan0)"
}
