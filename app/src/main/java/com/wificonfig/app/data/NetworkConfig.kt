package com.wificonfig.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 表示一套 Wi-Fi 静态 IP 网络配置
 */
data class StaticNetworkConfig(
    val ipAddress: String,
    val subnetPrefix: Int = 24,
    val gateway: String,
    val dnsPrimary: String,
    val dnsSecondary: String = ""
) {
    companion object {
        val Empty = StaticNetworkConfig(
            ipAddress = "",
            subnetPrefix = 24,
            gateway = "",
            dnsPrimary = "",
            dnsSecondary = ""
        )

        fun fromJson(jo: JSONObject): StaticNetworkConfig = StaticNetworkConfig(
            ipAddress = jo.optString("ipAddress", ""),
            subnetPrefix = jo.optInt("subnetPrefix", 24).takeIf { it in 1..32 } ?: 24,
            gateway = jo.optString("gateway", ""),
            dnsPrimary = jo.optString("dnsPrimary", ""),
            dnsSecondary = jo.optString("dnsSecondary", "")
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("ipAddress", ipAddress)
        .put("subnetPrefix", subnetPrefix)
        .put("gateway", gateway)
        .put("dnsPrimary", dnsPrimary)
        .put("dnsSecondary", dnsSecondary)

    fun isFilled(): Boolean {
        return ipAddress.isNotBlank() &&
                gateway.isNotBlank() &&
                dnsPrimary.isNotBlank() &&
                subnetPrefix in 1..32
    }

    /** 用于"另存为预设"时判断表单是否和已保存的一份相同（避免重复创建） */
    fun contentEquals(other: StaticNetworkConfig): Boolean {
        return this.ipAddress.trim() == other.ipAddress.trim()
                && this.subnetPrefix == other.subnetPrefix
                && this.gateway.trim() == other.gateway.trim()
                && this.dnsPrimary.trim() == other.dnsPrimary.trim()
                && this.dnsSecondary.trim() == other.dnsSecondary.trim()
    }
}

/**
 * 保存的预设方案（用户可保存最多 MAX_PRESETS 套）
 */
data class SavedPreset(
    val id: String,          // 唯一 ID（创建时生成 UUID 前 8 位 + 时间戳末 4 位）
    val name: String,        // 用户起的名（比如"公司 Wi-Fi"、"家里主路由 2.4G"）
    val config: StaticNetworkConfig,
    val createdAt: Long      // 创建时间戳（用于在 UI 上按时间倒序排列，最新的排前面）
) {
    companion object {
        const val MAX_PRESETS = 20

        fun fromJson(jo: JSONObject): SavedPreset = SavedPreset(
            id = jo.optString("id"),
            name = jo.optString("name"),
            config = StaticNetworkConfig.fromJson(jo.optJSONObject("config") ?: JSONObject()),
            createdAt = jo.optLong("createdAt", System.currentTimeMillis())
        )

        fun listFromJson(raw: String?): List<SavedPreset> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                val out = ArrayList<SavedPreset>(arr.length())
                for (i in 0 until arr.length()) {
                    runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()?.let { out.add(it) }
                }
                // 按创建时间倒序（最新的在前）
                out.sortedByDescending { it.createdAt }
            }.getOrDefault(emptyList())
        }

        fun listToJson(list: List<SavedPreset>): String {
            val arr = JSONArray()
            list.forEach { p ->
                arr.put(JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("config", p.config.toJson())
                    .put("createdAt", p.createdAt)
                )
            }
            return arr.toString()
        }
    }

    fun toOneLineSummary(): String = buildString {
        append(config.ipAddress).append(" / ").append(config.subnetPrefix)
        append(" → GW ").append(config.gateway)
        append("  DNS ").append(config.dnsPrimary)
        if (config.dnsSecondary.isNotBlank()) append("/").append(config.dnsSecondary)
    }
}

/**
 * 单条命令的诊断记录（用于在 UI 上展示每一步的成功 / 失败及输出）
 */
data class CommandDiagnostic(
    val index: Int,
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val ok: Boolean get() = exitCode == 0

    fun toDisplayLine(): String = buildString {
        append(if (ok) "✅" else "❌").append(' ').append('#').append(index)
            .append(" exit=").append(exitCode).append(' ').append(command)
        val so = stdout.trim()
        val se = stderr.trim()
        if (so.isNotEmpty()) append("\n      out: ").append(so.take(240))
        if (se.isNotEmpty()) append("\n      err: ").append(se.take(240))
    }
}

/**
 * 应用结果
 */
data class ApplyResult(
    val success: Boolean,
    val message: String,
    val diagnostics: List<CommandDiagnostic> = emptyList()
)

/**
 * 当前 Wi-Fi 接口信息
 */
data class WifiInterfaceInfo(
    val ifName: String,
    val currentIp: String = "",
    val currentGateway: String = "",
    val currentDns: String = ""
)
