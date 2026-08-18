package com.wificonfig.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wificonfig.app.data.ApplyResult
import com.wificonfig.app.data.CommandDiagnostic
import com.wificonfig.app.data.PreferencesRepository
import com.wificonfig.app.data.SavedPreset
import com.wificonfig.app.data.StaticNetworkConfig
import com.wificonfig.app.data.WifiInterfaceInfo
import java.util.UUID
import com.wificonfig.app.util.NetworkConfigManager
import com.wificonfig.app.util.RootShell
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 配置模式
 */
enum class ConfigMode { STATIC, DHCP }

/**
 * 操作状态
 */
sealed interface OperationStatus {
    object Idle : OperationStatus
    object CheckingRoot : OperationStatus
    object RootGranted : OperationStatus
    object RootDenied : OperationStatus
    object LoadingInterface : OperationStatus
    object Applying : OperationStatus
    data class Success(val message: String, val diagnostics: List<CommandDiagnostic> = emptyList()) : OperationStatus
    data class Failed(val message: String, val diagnostics: List<CommandDiagnostic> = emptyList()) : OperationStatus
    data class Info(val message: String) : OperationStatus
}

/**
 * UI 上弹起的预设对话框类型
 */
sealed interface PresetDialog {
    object CreateNew : PresetDialog
    data class Rename(val id: String, val currentName: String) : PresetDialog
    data class DeleteConfirm(val id: String, val name: String) : PresetDialog
    object ClearAll : PresetDialog
}

data class WifiConfigUiState(
    val mode: ConfigMode = ConfigMode.DHCP,
    val ip: String = "",
    val subnetPrefix: String = "24",
    val gateway: String = "",
    val dnsPrimary: String = "",
    val dnsSecondary: String = "",
    val ifaceInfo: WifiInterfaceInfo = WifiInterfaceInfo(""),
    val rootChecked: Boolean = false,
    val rootGranted: Boolean = false,
    val status: OperationStatus = OperationStatus.Idle,
    val statusMessage: String = "",
    val lastDiagnostics: List<CommandDiagnostic> = emptyList(),
    // 预设
    val presets: List<SavedPreset> = emptyList(),
    val selectedPresetId: String? = null,      // 当前选中的预设（chip 高亮）
    val pendingPresetDialog: PresetDialog? = null
)

class WifiConfigViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** 应用静态 IP：整体超时（单命令还有 3~5 秒级超时兜底） */
        private const val TOTAL_APPLY_TIMEOUT_MS = 15_000L
        /** DHCP 还原：含 sleep 2 + Wi-Fi disable/enable，给更长超时 */
        private const val TOTAL_DHCP_TIMEOUT_MS = 20_000L
    }

    private val repository = PreferencesRepository(application)

    private val _uiState = MutableStateFlow(WifiConfigUiState())
    val uiState: StateFlow<WifiConfigUiState> = _uiState.asStateFlow()

    private val _snackbarChannel = Channel<String>(Channel.BUFFERED)
    val snackbarFlow: Flow<String> = _snackbarChannel.receiveAsFlow()

    init {
        // 加载本地保存的配置
        viewModelScope.launch {
            launch {
                repository.staticConfigFlow.collect { cfg ->
                    _uiState.update {
                        it.copy(
                            ip = cfg.ipAddress,
                            subnetPrefix = cfg.subnetPrefix.toString(),
                            gateway = cfg.gateway,
                            dnsPrimary = cfg.dnsPrimary,
                            dnsSecondary = cfg.dnsSecondary
                        )
                    }
                }
            }
            launch {
                repository.lastModeFlow.collect { lastMode ->
                    val m = if (lastMode == PreferencesRepository.MODE_STATIC)
                        ConfigMode.STATIC else ConfigMode.DHCP
                    _uiState.update { it.copy(mode = m) }
                }
            }
            launch {
                repository.presetsFlow.collect { presets ->
                    _uiState.update { state ->
                        // 如果之前选中的预设被删了，清掉选中态
                        val stillExists = state.selectedPresetId?.let { id -> presets.any { it.id == id } } ?: true
                        state.copy(
                            presets = presets,
                            selectedPresetId = state.selectedPresetId.takeIf { stillExists }
                        )
                    }
                }
            }
        }
    }

    // ============== 公共方法供 UI 调用 ==============

    fun checkRootAndDetectInterface() {
        viewModelScope.launch {
            setStatus(OperationStatus.CheckingRoot, "正在检查 Root 权限…")
            val hasRoot = RootShell.checkRootAccess()
            if (hasRoot) {
                setStatus(OperationStatus.RootGranted, "Root 权限已获取")
                _uiState.update { it.copy(rootChecked = true, rootGranted = true) }
                detectInterface()
            } else {
                setStatus(OperationStatus.RootDenied, "未获取 Root 权限，无法修改网络配置")
                _uiState.update { it.copy(rootChecked = true, rootGranted = false) }
                _snackbarChannel.send("未获取 Root 权限。请确认设备已 Root，并在弹窗中授予本应用权限。")
            }
        }
    }

    private suspend fun detectInterface() {
        setStatus(OperationStatus.LoadingInterface, "正在探测 Wi-Fi 接口…")
        val info = NetworkConfigManager.detectWifiInterface()
        _uiState.update { it.copy(ifaceInfo = info) }
        if (info.ifName.isEmpty()) {
            setStatus(OperationStatus.Failed("未检测到网络接口。请确保 Wi-Fi 已连接。"), "")
        } else {
            setStatus(OperationStatus.Idle, "就绪：接口 ${info.ifName}（当前 IP: ${info.currentIp.ifBlank { "-" }}）")
        }
    }

    fun updateMode(mode: ConfigMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(mode = mode) }
            repository.saveLastMode(
                when (mode) {
                    ConfigMode.STATIC -> PreferencesRepository.MODE_STATIC
                    ConfigMode.DHCP -> PreferencesRepository.MODE_DHCP
                }
            )
        }
    }

    fun updateIp(value: String) { _uiState.update { it.copy(ip = value) } }
    fun updateSubnet(value: String) {
        val filtered = value.filter { it.isDigit() }.take(2)
        _uiState.update { it.copy(subnetPrefix = filtered) }
    }
    fun updateGateway(value: String) { _uiState.update { it.copy(gateway = value) } }
    fun updateDnsPrimary(value: String) { _uiState.update { it.copy(dnsPrimary = value) } }
    fun updateDnsSecondary(value: String) { _uiState.update { it.copy(dnsSecondary = value) } }

    fun saveConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            val prefixInt = state.subnetPrefix.toIntOrNull() ?: 24
            val cfg = StaticNetworkConfig(
                ipAddress = state.ip.trim(),
                subnetPrefix = prefixInt.coerceIn(1, 32),
                gateway = state.gateway.trim(),
                dnsPrimary = state.dnsPrimary.trim(),
                dnsSecondary = state.dnsSecondary.trim()
            )
            repository.saveStaticConfig(cfg)
            setStatus(OperationStatus.Success("已保存"), "配置已保存到本地")
            _snackbarChannel.send("配置已保存")
        }
    }

    fun applyCurrentMode() {
        val state = _uiState.value
        if (!state.rootGranted) {
            viewModelScope.launch {
                _snackbarChannel.send("请先授予 Root 权限后再应用配置")
                checkRootAndDetectInterface()
            }
            return
        }
        when (state.mode) {
            ConfigMode.STATIC -> applyStaticConfig()
            ConfigMode.DHCP -> applyDhcpConfig()
        }
    }

    private fun applyStaticConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            val prefixInt = state.subnetPrefix.toIntOrNull() ?: 24
            val cfg = StaticNetworkConfig(
                ipAddress = state.ip.trim(),
                subnetPrefix = prefixInt.coerceIn(1, 32),
                gateway = state.gateway.trim(),
                dnsPrimary = state.dnsPrimary.trim(),
                dnsSecondary = state.dnsSecondary.trim()
            )
            if (!cfg.isFilled()) {
                _snackbarChannel.send("静态配置不完整，请填写 IP、网关与主 DNS")
                return@launch
            }
            // 先保存一次
            repository.saveStaticConfig(cfg)

            setStatus(OperationStatus.Applying, "正在应用静态 IP 配置…（最长等待 15s / 单命令超时 3~5s）")
            val iface = state.ifaceInfo.ifName.ifBlank {
                // 重新检测一次
                val info = NetworkConfigManager.detectWifiInterface()
                _uiState.update { it.copy(ifaceInfo = info) }
                info.ifName
            }
            val result = try {
                withTimeout(TOTAL_APPLY_TIMEOUT_MS) {
                    NetworkConfigManager.applyStaticIp(iface, cfg)
                }
            } catch (e: TimeoutCancellationException) {
                ApplyResult(
                    false,
                    "应用静态 IP 超时（超过 15 秒）。请展开下方诊断日志查看哪一条命令卡住了，截图发给作者。" +
                            "已做单命令级超时兜底：若你仍能看到下面的诊断条目，则说明某些命令累计超过了总时长。",
                    _uiState.value.lastDiagnostics
                )
            } catch (t: Throwable) {
                ApplyResult(
                    false,
                    "应用静态 IP 异常：${t.javaClass.simpleName}: ${t.message ?: "Unknown"}",
                    _uiState.value.lastDiagnostics
                )
            }
            handleApplyResult(result, "静态 IP 配置应用成功")
        }
    }

    private fun applyDhcpConfig() {
        viewModelScope.launch {
            setStatus(OperationStatus.Applying, "正在切换到 DHCP 模式…（最长等待 20s，因为会自动重启 Wi-Fi）")
            val state = _uiState.value
            val iface = state.ifaceInfo.ifName.ifBlank {
                val info = NetworkConfigManager.detectWifiInterface()
                _uiState.update { it.copy(ifaceInfo = info) }
                info.ifName
            }
            val result = try {
                // DHCP 会有 sleep 2 + wifi disable/enable，给更长的整体超时
                withTimeout(TOTAL_DHCP_TIMEOUT_MS) {
                    NetworkConfigManager.enableDhcp(iface)
                }
            } catch (e: TimeoutCancellationException) {
                ApplyResult(
                    false,
                    "切换 DHCP 超时（超过 20 秒）。通常是因为 `svc wifi disable; sleep 2; svc wifi enable` " +
                            "被 MIUI 权限拦截。你可以手动关 Wi-Fi 再开模拟 DHCP 重连；或者展开下方诊断定位具体卡住的命令。",
                    _uiState.value.lastDiagnostics
                )
            } catch (t: Throwable) {
                ApplyResult(
                    false,
                    "切换 DHCP 异常：${t.javaClass.simpleName}: ${t.message ?: "Unknown"}",
                    _uiState.value.lastDiagnostics
                )
            }
            handleApplyResult(result, "已切换到 DHCP 模式，等待自动获取…")
        }
    }

    private suspend fun handleApplyResult(result: ApplyResult, successMsg: String) {
        val diag = result.diagnostics
        if (result.success) {
            setStatus(OperationStatus.Success(result.message, diag), successMsg, diag)
            _snackbarChannel.send(result.message)
            // 善意提醒：系统设置界面没同步是正常现象，底层已生效
            // 放在第二条，Snackbar 会自动排队在"成功"消息消失后显示
            _snackbarChannel.send(
                buildString {
                    append("💡 小提醒：系统设置里的 Wi-Fi 页面显示的 IP/网关/DNS 可能暂时没变化，")
                    append("但安卓底层的网络设置其实已经改好并生效啦 ✅。")
                    append("要是想让系统设置页面也同步显示，只需关闭再打开 Wi-Fi，重连一次就好。")
                }
            )
            // 应用成功后重新探测接口信息以便 UI 展示
            val info = NetworkConfigManager.detectWifiInterface()
            _uiState.update { it.copy(ifaceInfo = info) }
        } else {
            setStatus(OperationStatus.Failed(result.message, diag), result.message, diag)
            _snackbarChannel.send(result.message)
        }
    }

    private fun setStatus(status: OperationStatus, message: String, diagnostics: List<CommandDiagnostic> = emptyList()) {
        _uiState.update {
            it.copy(
                status = status,
                statusMessage = message,
                lastDiagnostics = diagnostics.ifEmpty { it.lastDiagnostics }
            )
        }
    }

    // ============== 预设方案：对外 actions ==============

    private fun closePresetDialog() {
        _uiState.update { it.copy(pendingPresetDialog = null) }
    }

    fun openCreatePresetDialog() {
        val s = _uiState.value
        if (!currentStaticConfig().isFilled()) {
            viewModelScope.launch { _snackbarChannel.send("请先把上方 5 个字段填完整，再另存为预设。") }
            return
        }
        if (s.presets.size >= SavedPreset.MAX_PRESETS) {
            viewModelScope.launch {
                _snackbarChannel.send("已达上限 ${SavedPreset.MAX_PRESETS} 套预设。请先删除不常用的。")
            }
            return
        }
        _uiState.update { it.copy(pendingPresetDialog = PresetDialog.CreateNew) }
    }

    fun openRenamePresetDialog(id: String) {
        val p = _uiState.value.presets.firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(pendingPresetDialog = PresetDialog.Rename(id, p.name)) }
    }

    fun openDeletePresetDialog(id: String) {
        val p = _uiState.value.presets.firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(pendingPresetDialog = PresetDialog.DeleteConfirm(id, p.name)) }
    }

    fun openClearAllPresetsDialog() {
        if (_uiState.value.presets.isEmpty()) return
        _uiState.update { it.copy(pendingPresetDialog = PresetDialog.ClearAll) }
    }

    fun cancelPresetDialog() = closePresetDialog()

    /** 把当前表单内容另存为一个新预设（对话框确认按钮触发） */
    fun confirmCreatePreset(nameInput: String) {
        viewModelScope.launch {
            val cfg = currentStaticConfig()
            if (!cfg.isFilled()) {
                _snackbarChannel.send("表单内容不完整，无法另存为预设。")
                closePresetDialog()
                return@launch
            }
            val name = nameInput.trim().ifBlank {
                // 默认名："预设 #(count+1)"
                val n = _uiState.value.presets.size + 1
                "预设 $n"
            }
            val dup = _uiState.value.presets.firstOrNull { it.name == name }
            if (dup != null && dup.config.contentEquals(cfg)) {
                _snackbarChannel.send("内容相同且同名的预设已存在（「$name」），无需重复保存。")
                closePresetDialog()
                return@launch
            }
            val id = UUID.randomUUID().toString().take(8) + "_" + System.currentTimeMillis().toString().takeLast(4)
            val preset = SavedPreset(id = id, name = name, config = cfg, createdAt = System.currentTimeMillis())
            val ok = repository.addPreset(preset)
            if (ok) {
                _snackbarChannel.send("已保存预设「$name」（${_uiState.value.presets.size + 1}/${SavedPreset.MAX_PRESETS}）")
                _uiState.update { it.copy(selectedPresetId = id) }
            } else {
                _snackbarChannel.send("保存失败：已达 ${SavedPreset.MAX_PRESETS} 套上限，或 ID 冲突。")
            }
            closePresetDialog()
        }
    }

    fun confirmRenamePreset(id: String, newNameInput: String) {
        viewModelScope.launch {
            val newName = newNameInput.trim().ifBlank {
                _snackbarChannel.send("新名称不能为空。")
                return@launch
            }
            repository.renamePreset(id, newName)
            _snackbarChannel.send("预设已重命名为「$newName」")
            closePresetDialog()
        }
    }

    fun confirmDeletePreset(id: String) {
        viewModelScope.launch {
            repository.deletePreset(id)
            _snackbarChannel.send("预设已删除")
            closePresetDialog()
        }
    }

    fun confirmClearAllPresets() {
        viewModelScope.launch {
            repository.clearAllPresets()
            _uiState.update { it.copy(selectedPresetId = null) }
            _snackbarChannel.send("已清空全部预设")
            closePresetDialog()
        }
    }

    /** 点击预设 chip：把它的内容填进表单 + 切到静态模式 + 高亮选中 */
    fun loadPreset(id: String) {
        val p = _uiState.value.presets.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                selectedPresetId = id,
                mode = ConfigMode.STATIC,
                ip = p.config.ipAddress,
                subnetPrefix = p.config.subnetPrefix.toString(),
                gateway = p.config.gateway,
                dnsPrimary = p.config.dnsPrimary,
                dnsSecondary = p.config.dnsSecondary
            )
        }
        viewModelScope.launch {
            repository.saveLastMode(PreferencesRepository.MODE_STATIC)
            repository.saveStaticConfig(p.config)
            _snackbarChannel.send("已载入预设「${p.name}」，已自动切到静态模式。")
        }
    }

    // ============== 内部工具 ==============

    private fun currentStaticConfig(): StaticNetworkConfig {
        val s = _uiState.value
        val prefix = s.subnetPrefix.toIntOrNull() ?: 24
        return StaticNetworkConfig(
            ipAddress = s.ip.trim(),
            subnetPrefix = prefix.coerceIn(1, 32),
            gateway = s.gateway.trim(),
            dnsPrimary = s.dnsPrimary.trim(),
            dnsSecondary = s.dnsSecondary.trim()
        )
    }
}
