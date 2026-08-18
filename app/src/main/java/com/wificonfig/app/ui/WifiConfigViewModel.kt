package com.wificonfig.app.ui

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wificonfig.app.data.ApplyResult
import com.wificonfig.app.data.CommandDiagnostic
import com.wificonfig.app.data.PreferencesRepository
import com.wificonfig.app.data.SavedPreset
import com.wificonfig.app.data.StaticNetworkConfig
import com.wificonfig.app.data.WifiInterfaceInfo
import com.wificonfig.app.data.resolveAppStrings
import java.util.Locale
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
    val pendingPresetDialog: PresetDialog? = null,
    // 语言
    val languageOption: LanguageOption = LanguageOption.SYSTEM,
    val showLanguageDialog: Boolean = false
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

    // ==================== 语言：当前 AppStrings ====================
    /** 当前系统 Locale（取 App 的 Configuration）；后续也可以通过 onConfigurationChanged 更新 */
    private val _systemLocale = MutableStateFlow(getSystemLocale())
    val systemLocale: StateFlow<Locale> = _systemLocale.asStateFlow()

    /** 最终解析出来的 AppStrings：结合「用户选择 + 系统 Locale」 */
    val appStringsState: StateFlow<AppStrings> =
        combine(
            repository.languageOptionFlow,
            systemLocale
        ) { opt, loc -> resolveAppStrings(opt, loc) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, resolveAppStrings(LanguageOption.SYSTEM, getSystemLocale()))

    /** 当前使用的语言选项（system/zh/en），放 UiState 供对话框勾选 */
    private val _languageOptionState: StateFlow<LanguageOption> =
        repository.languageOptionFlow.stateIn(viewModelScope, SharingStarted.Eagerly, LanguageOption.SYSTEM)

    /** 上一次状态/消息的「构造函数」，用于语言切换时重新渲染 */
    private var statusFn: (AppStrings) -> OperationStatus = { OperationStatus.Idle }
    private var statusMsgFn: (AppStrings) -> String = { s -> s.statusIdleReady }
    private var lastDiagnosticsMemo: List<CommandDiagnostic> = emptyList()

    init {
        // 语言切换 → 重算 appStrings → 重绘 statusMessage / OperationStatus（带翻译的 message）
        viewModelScope.launch {
            appStringsState.collect { newStrings ->
                refreshStatusWithStrings(newStrings)
                // 同时把 languageOption 同步到 UiState
                _uiState.update { it.copy(languageOption = _languageOptionState.value) }
            }
        }

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

    // ============ 语言：对外 actions ============
    fun openLanguageDialog() {
        _uiState.update { it.copy(showLanguageDialog = true) }
    }

    fun dismissLanguageDialog() {
        _uiState.update { it.copy(showLanguageDialog = false) }
    }

    fun selectLanguage(option: LanguageOption) {
        viewModelScope.launch {
            repository.saveLanguageOption(option)
            dismissLanguageDialog()
            _snackbarChannel.send(
                when (option) {
                    LanguageOption.SYSTEM -> appStringsState.value.langSnackbarFollowSystem
                    LanguageOption.ZH -> appStringsState.value.langSnackbarZh
                    LanguageOption.EN -> appStringsState.value.langSnackbarEn
                }
            )
        }
    }

    // ============== 公共方法供 UI 调用 ==============

    fun checkRootAndDetectInterface() {
        viewModelScope.launch {
            setStatus(
                statusFn = { s -> OperationStatus.CheckingRoot },
                msgFn = { s -> s.statusCheckingRoot }
            )
            val hasRoot = RootShell.checkRootAccess()
            if (hasRoot) {
                setStatus(
                    statusFn = { s -> OperationStatus.RootGranted },
                    msgFn = { s -> s.statusRootGranted }
                )
                _uiState.update { it.copy(rootChecked = true, rootGranted = true) }
                detectInterface()
            } else {
                setStatus(
                    statusFn = { s -> OperationStatus.RootDenied },
                    msgFn = { s -> s.statusRootDenied }
                )
                _uiState.update { it.copy(rootChecked = true, rootGranted = false) }
                _snackbarChannel.send(appStringsState.value.snackbarRootNotGranted)
            }
        }
    }

    private suspend fun detectInterface() {
        setStatus(
            statusFn = { s -> OperationStatus.LoadingInterface },
            msgFn = { s -> s.statusDetectingIface }
        )
        val info = NetworkConfigManager.detectWifiInterface()
        _uiState.update { it.copy(ifaceInfo = info) }
        if (info.ifName.isEmpty()) {
            setStatus(
                statusFn = { s ->
                    val m = s.statusNoIface
                    OperationStatus.Failed(m)
                },
                msgFn = { s -> s.statusNoIface },
                diagnosticsFetcher = { emptyList() }
            )
        } else {
            setStatus(
                statusFn = { s -> OperationStatus.Idle },
                msgFn = { s ->
                    s.statusReadyWithIface(info.ifName, info.currentIp.ifBlank { "-" })
                }
            )
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
            val s = appStringsState.value
            setStatus(
                statusFn = { ss -> OperationStatus.Success(ss.msgSaved) },
                msgFn = { ss -> ss.statusSavedLocal }
            )
            _snackbarChannel.send(s.snackbarConfigSaved)
        }
    }

    fun applyCurrentMode() {
        val state = _uiState.value
        if (!state.rootGranted) {
            viewModelScope.launch {
                _snackbarChannel.send(appStringsState.value.snackbarApplyNeedRootFirst)
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
                _snackbarChannel.send(appStringsState.value.snackbarStaticIncomplete)
                return@launch
            }
            // 先保存一次
            repository.saveStaticConfig(cfg)

            setStatus(
                statusFn = { s -> OperationStatus.Applying },
                msgFn = { s -> s.statusApplyingStatic }
            )
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
                val diag = _uiState.value.lastDiagnostics
                val fnMsg: (AppStrings) -> String = { s -> s.statusApplyStaticTimeout }
                ApplyResult(false, fnMsg, diag)
            } catch (t: Throwable) {
                val diag = _uiState.value.lastDiagnostics
                val fnMsg: (AppStrings) -> String = { s ->
                    s.statusApplyStaticException(t.javaClass.simpleName, t.message ?: "Unknown")
                }
                ApplyResult(false, fnMsg, diag)
            }
            val successMsgFn: (AppStrings) -> String = { s -> s.statusStaticAppliedOk }
            handleApplyResult(result, successMsgFn)
        }
    }

    private fun applyDhcpConfig() {
        viewModelScope.launch {
            setStatus(
                statusFn = { s -> OperationStatus.Applying },
                msgFn = { s -> s.statusApplyingDhcp }
            )
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
                val diag = _uiState.value.lastDiagnostics
                val fnMsg: (AppStrings) -> String = { s -> s.statusDhcpTimeout }
                ApplyResult(false, fnMsg, diag)
            } catch (t: Throwable) {
                val diag = _uiState.value.lastDiagnostics
                val fnMsg: (AppStrings) -> String = { s ->
                    s.statusDhcpException(t.javaClass.simpleName, t.message ?: "Unknown")
                }
                ApplyResult(false, fnMsg, diag)
            }
            val successMsgFn: (AppStrings) -> String = { s -> s.statusDhcpAppliedOk }
            handleApplyResult(result, successMsgFn)
        }
    }

    private suspend fun handleApplyResult(
        result: ApplyResult,
        successMsgFn: (AppStrings) -> String
    ) {
        val diag = result.diagnostics
        // 注意：ApplyResult.message 现在可能是 String 或 function —— 我们在 data 包把它改成 Any/或者统一用 function。
        // 为了最小改动，我们用 ApplyResult.messageFn 字段（(AppStrings)->String?），若为 null 则回退到 message 字段。
        val msgFn = result.messageFn ?: { _: AppStrings -> result.message }

        if (result.success) {
            setStatus(
                statusFn = { s -> OperationStatus.Success(msgFn(s), diag) },
                msgFn = successMsgFn,
                diagnosticsFetcher = { diag }
            )
            _snackbarChannel.send(msgFn(appStringsState.value))
            // 善意提醒：系统设置界面没同步是正常现象，底层已生效
            _snackbarChannel.send(appStringsState.value.snackbarSystemSettingsNotice)
            // 应用成功后重新探测接口信息以便 UI 展示
            val info = NetworkConfigManager.detectWifiInterface()
            _uiState.update { it.copy(ifaceInfo = info) }
        } else {
            setStatus(
                statusFn = { s -> OperationStatus.Failed(msgFn(s), diag) },
                msgFn = msgFn,
                diagnosticsFetcher = { diag }
            )
            _snackbarChannel.send(msgFn(appStringsState.value))
        }
    }

    /**
     * 核心：设置「可翻译」的状态与消息。
     *   @param statusFn 根据 AppStrings 生成最终 OperationStatus（含其内部 message 字段）
     *   @param msgFn    根据 AppStrings 生成 statusMessage 字符串
     *   @param diagnosticsFetcher 返回诊断列表（此为 List，非函数，因为它不是翻译相关）
     */
    private fun setStatus(
        statusFn: (AppStrings) -> OperationStatus,
        msgFn: (AppStrings) -> String,
        diagnosticsFetcher: () -> List<CommandDiagnostic> = { emptyList() }
    ) {
        this.statusFn = statusFn
        this.statusMsgFn = msgFn
        val diag = diagnosticsFetcher()
        if (diag.isNotEmpty()) this.lastDiagnosticsMemo = diag
        refreshStatusWithStrings(appStringsState.value)
    }

    /** 用当前 AppStrings 重新渲染一次状态与消息（语言切换时调用） */
    private fun refreshStatusWithStrings(strings: AppStrings) {
        val status = statusFn(strings)
        val msg = statusMsgFn(strings)
        _uiState.update {
            it.copy(
                status = status,
                statusMessage = msg,
                lastDiagnostics = lastDiagnosticsMemo.ifEmpty { it.lastDiagnostics }
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
            viewModelScope.launch { _snackbarChannel.send(appStringsState.value.snackbarPresetFillFirst) }
            return
        }
        if (s.presets.size >= SavedPreset.MAX_PRESETS) {
            viewModelScope.launch {
                _snackbarChannel.send(
                    appStringsState.value.snackbarPresetMaxReached(SavedPreset.MAX_PRESETS)
                )
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
            val s = appStringsState.value
            if (!cfg.isFilled()) {
                _snackbarChannel.send(s.snackbarPresetIncomplete)
                closePresetDialog()
                return@launch
            }
            val name = nameInput.trim().ifBlank {
                val n = _uiState.value.presets.size + 1
                s.defaultPresetName(n)
            }
            val dup = _uiState.value.presets.firstOrNull { it.name == name }
            if (dup != null && dup.config.contentEquals(cfg)) {
                _snackbarChannel.send(s.snackbarPresetDupExists(name))
                closePresetDialog()
                return@launch
            }
            val id = UUID.randomUUID().toString().take(8) + "_" + System.currentTimeMillis().toString().takeLast(4)
            val preset = SavedPreset(id = id, name = name, config = cfg, createdAt = System.currentTimeMillis())
            val ok = repository.addPreset(preset)
            if (ok) {
                _snackbarChannel.send(
                    s.snackbarPresetSaved(name, _uiState.value.presets.size + 1, SavedPreset.MAX_PRESETS)
                )
                _uiState.update { it.copy(selectedPresetId = id) }
            } else {
                _snackbarChannel.send(s.snackbarPresetSaveFail(SavedPreset.MAX_PRESETS))
            }
            closePresetDialog()
        }
    }

    fun confirmRenamePreset(id: String, newNameInput: String) {
        viewModelScope.launch {
            val s = appStringsState.value
            val newName = newNameInput.trim().ifBlank {
                _snackbarChannel.send(s.snackbarPresetRenameEmpty)
                return@launch
            }
            repository.renamePreset(id, newName)
            _snackbarChannel.send(s.snackbarPresetRenamed(newName))
            closePresetDialog()
        }
    }

    fun confirmDeletePreset(id: String) {
        viewModelScope.launch {
            repository.deletePreset(id)
            _snackbarChannel.send(appStringsState.value.snackbarPresetDeleted)
            closePresetDialog()
        }
    }

    fun confirmClearAllPresets() {
        viewModelScope.launch {
            repository.clearAllPresets()
            _uiState.update { it.copy(selectedPresetId = null) }
            _snackbarChannel.send(appStringsState.value.snackbarPresetAllCleared)
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
            _snackbarChannel.send(appStringsState.value.snackbarPresetLoaded(p.name))
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

    @Suppress("DEPRECATION")
    private fun getSystemLocale(): Locale {
        val app = getApplication<Application>()
        val config: Configuration = app.resources.configuration
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.locales[0]
        } else {
            config.locale
        } ?: Locale.getDefault()
    }
}
