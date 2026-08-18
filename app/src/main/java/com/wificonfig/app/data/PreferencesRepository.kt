package com.wificonfig.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 全局单例 DataStore（Context 扩展属性）
 */
val Context.wifiConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "wifi_config_prefs")

/**
 * 使用 Jetpack DataStore 持久化用户输入的静态网络配置
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val IP_ADDRESS = stringPreferencesKey("ip_address")
        val SUBNET_PREFIX = intPreferencesKey("subnet_prefix")
        val GATEWAY = stringPreferencesKey("gateway")
        val DNS_PRIMARY = stringPreferencesKey("dns_primary")
        val DNS_SECONDARY = stringPreferencesKey("dns_secondary")
        val LAST_MODE = stringPreferencesKey("last_mode") // "STATIC" or "DHCP"
        val PRESETS_JSON = stringPreferencesKey("presets_json")  // 所有 SavedPreset 拼成一个 JSON 数组
    }

    companion object {
        const val MODE_STATIC = "STATIC"
        const val MODE_DHCP = "DHCP"
    }

    /**
     * 读取保存的静态配置流
     */
    val staticConfigFlow: Flow<StaticNetworkConfig> =
        context.wifiConfigDataStore.data.map { prefs ->
            StaticNetworkConfig(
                ipAddress = prefs[Keys.IP_ADDRESS].orEmpty(),
                subnetPrefix = prefs[Keys.SUBNET_PREFIX] ?: 24,
                gateway = prefs[Keys.GATEWAY].orEmpty(),
                dnsPrimary = prefs[Keys.DNS_PRIMARY].orEmpty(),
                dnsSecondary = prefs[Keys.DNS_SECONDARY].orEmpty()
            )
        }

    /**
     * 读取上次选择的模式
     */
    val lastModeFlow: Flow<String> =
        context.wifiConfigDataStore.data.map { prefs ->
            prefs[Keys.LAST_MODE] ?: MODE_DHCP
        }

    /**
     * 保存一套静态网络配置
     */
    suspend fun saveStaticConfig(config: StaticNetworkConfig) {
        context.wifiConfigDataStore.edit { prefs ->
            prefs[Keys.IP_ADDRESS] = config.ipAddress
            prefs[Keys.SUBNET_PREFIX] = config.subnetPrefix
            prefs[Keys.GATEWAY] = config.gateway
            prefs[Keys.DNS_PRIMARY] = config.dnsPrimary
            prefs[Keys.DNS_SECONDARY] = config.dnsSecondary
        }
    }

    /**
     * 记录当前用户选择的模式
     */
    suspend fun saveLastMode(mode: String) {
        context.wifiConfigDataStore.edit { prefs ->
            prefs[Keys.LAST_MODE] = mode
        }
    }

    // ============== 预设方案（SavedPreset）管理 ==============

    val presetsFlow: Flow<List<SavedPreset>> =
        context.wifiConfigDataStore.data.map { prefs ->
            SavedPreset.listFromJson(prefs[Keys.PRESETS_JSON])
        }

    /**
     * 新增一个预设；超过 MAX_PRESETS 会返回 false
     */
    suspend fun addPreset(preset: SavedPreset): Boolean {
        var added = false
        context.wifiConfigDataStore.edit { prefs ->
            val list = SavedPreset.listFromJson(prefs[Keys.PRESETS_JSON]).toMutableList()
            if (list.size >= SavedPreset.MAX_PRESETS) return@edit
            // 避免重复 ID
            if (list.any { it.id == preset.id }) return@edit
            list.add(preset)
            prefs[Keys.PRESETS_JSON] = SavedPreset.listToJson(list)
            added = true
        }
        return added
    }

    suspend fun renamePreset(id: String, newName: String) {
        context.wifiConfigDataStore.edit { prefs ->
            val list = SavedPreset.listFromJson(prefs[Keys.PRESETS_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(name = newName.trim().ifBlank { list[idx].name })
                prefs[Keys.PRESETS_JSON] = SavedPreset.listToJson(list)
            }
        }
    }

    suspend fun deletePreset(id: String) {
        context.wifiConfigDataStore.edit { prefs ->
            val list = SavedPreset.listFromJson(prefs[Keys.PRESETS_JSON]).toMutableList()
            val removed = list.removeAll { it.id == id }
            if (removed) prefs[Keys.PRESETS_JSON] = SavedPreset.listToJson(list)
        }
    }

    suspend fun clearAllPresets() {
        context.wifiConfigDataStore.edit { prefs ->
            prefs[Keys.PRESETS_JSON] = SavedPreset.listToJson(emptyList())
        }
    }
}
