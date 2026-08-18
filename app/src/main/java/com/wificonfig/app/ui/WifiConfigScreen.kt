package com.wificonfig.app.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wificonfig.app.ui.theme.ErrorRed
import com.wificonfig.app.ui.theme.SuccessGreen
import com.wificonfig.app.ui.theme.WifiConfigAppTheme
import com.wificonfig.app.ui.theme.WarningOrange

// =====================================================
//  主入口：整个应用的单页界面
//  设计原则：面向小白用户 → 一眼看懂，按钮大，少废话
// =====================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiConfigScreen(viewModel: WifiConfigViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Snackbar 消息收集（不重复 toast）
    LaunchedEffect(Unit) {
        viewModel.snackbarFlow.collect { msg ->
            // 善意提醒是长文，用 Long 停留更久；普通消息用 Short
            val duration = if (msg.startsWith("💡 小提醒") || msg.contains("系统设置里的 Wi-Fi")) {
                androidx.compose.material3.SnackbarDuration.Long
            } else {
                androidx.compose.material3.SnackbarDuration.Short
            }
            snackbarHostState.showSnackbar(msg, duration = duration)
        }
    }
    // 首次启动检查 Root + 探测 Wi-Fi 接口
    LaunchedEffect(Unit) {
        if (!uiState.rootChecked) {
            viewModel.checkRootAndDetectInterface()
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = context.getString(com.wificonfig.app.R.string.app_name),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = "一键改 Wi-Fi 的 IP / DNS · 需要 Root 权限",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---------- 1. 当前状态（Root + 接口信息，合并成一张卡片） ----------
                CurrentStatusCard(
                    uiState = uiState,
                    onRefresh = { viewModel.checkRootAndDetectInterface() }
                )

                // ---------- 2. 大按钮模式选择（小白友好，自动 vs 手动） ----------
                ModeSelectorBig(
                    uiState = uiState,
                    onModeChanged = { viewModel.updateMode(it) }
                )

                // ---------- 3a. 静态模式：展开表单 ----------
                AnimatedVisibility(
                    visible = uiState.mode == ConfigMode.STATIC,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StaticFormCard(
                            uiState = uiState,
                            onIp = { viewModel.updateIp(it) },
                            onSubnet = { viewModel.updateSubnet(it) },
                            onGateway = { viewModel.updateGateway(it) },
                            onDnsPrimary = { viewModel.updateDnsPrimary(it) },
                            onDnsSecondary = { viewModel.updateDnsSecondary(it) }
                        )
                        PresetsCard(
                            uiState = uiState,
                            onCreate = { viewModel.openCreatePresetDialog() },
                            onRename = { viewModel.openRenamePresetDialog(it) },
                            onDelete = { viewModel.openDeletePresetDialog(it) },
                            onClearAll = { viewModel.openClearAllPresetsDialog() },
                            onLoad = { viewModel.loadPreset(it) }
                        )
                    }
                }

                // ---------- 3b. DHCP 模式：只给一句贴心提示 ----------
                AnimatedVisibility(
                    visible = uiState.mode == ConfigMode.DHCP,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    DhcpHintCard()
                }

                // ---------- 4. 主操作按钮栏（保存 + 应用/切换） ----------
                MainCtaBar(
                    uiState = uiState,
                    onSave = { viewModel.saveConfig() },
                    onApply = { viewModel.applyCurrentMode() }
                )

                // ---------- 5. 状态横幅（成功/失败/进行中） ----------
                StatusBanner(uiState = uiState)

                // ---------- 6. 诊断日志（默认收起；只有有失败命令时用红色醒目提示） ----------
                if (uiState.lastDiagnostics.isNotEmpty()) {
                    DiagnosticsCard(uiState = uiState)
                }

                Spacer(Modifier.height(14.dp))
            }
        }

        // ---------- 预设相关对话框（弹窗不参与滚动） ----------
        val dlg = uiState.pendingPresetDialog
        if (dlg != null) {
            when (dlg) {
                is PresetDialog.CreateNew -> PresetCreateDialog(
                    onCancel = { viewModel.cancelPresetDialog() },
                    onConfirm = { name -> viewModel.confirmCreatePreset(name) }
                )
                is PresetDialog.Rename -> PresetRenameDialog(
                    currentName = dlg.currentName,
                    onCancel = { viewModel.cancelPresetDialog() },
                    onConfirm = { newName -> viewModel.confirmRenamePreset(dlg.id, newName) }
                )
                is PresetDialog.DeleteConfirm -> PresetDeleteDialog(
                    presetName = dlg.name,
                    onCancel = { viewModel.cancelPresetDialog() },
                    onConfirm = { viewModel.confirmDeletePreset(dlg.id) }
                )
                PresetDialog.ClearAll -> PresetClearAllDialog(
                    count = uiState.presets.size,
                    onCancel = { viewModel.cancelPresetDialog() },
                    onConfirm = { viewModel.confirmClearAllPresets() }
                )
            }
        }
    }
}

// =====================================================
//  1. 当前状态卡片（Root 状态 + Wi-Fi 接口信息合并）
// =====================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrentStatusCard(uiState: WifiConfigUiState, onRefresh: () -> Unit) {
    val rootOk = uiState.rootChecked && uiState.rootGranted
    val rootChecking = !uiState.rootChecked ||
            uiState.status is OperationStatus.CheckingRoot ||
            uiState.status is OperationStatus.LoadingInterface
    val iface = uiState.ifaceInfo

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rootOk)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                WarningOrange.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 顶行：Root 权限状态
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                rootChecking -> WarningOrange.copy(alpha = 0.18f)
                                rootOk -> SuccessGreen.copy(alpha = 0.18f)
                                else -> ErrorRed.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (rootChecking) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp),
                            color = WarningOrange
                        )
                    } else {
                        Icon(
                            imageVector = if (rootOk) Icons.Default.Verified else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (rootOk) SuccessGreen else ErrorRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (rootChecking) "正在检查 Root 权限…"
                        else if (rootOk) "Root 权限已就绪 ✓"
                        else "⚠️ 还没有 Root 权限",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (rootOk) "可以放心修改网络设置啦。"
                        else "请用 Magisk 等工具给本应用授权 SuperSU，否则改网络不会生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "刷新网络状态")
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 底行：当前 Wi-Fi 接口信息
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (iface.ifName.isBlank()) "未检测到 Wi-Fi" else "当前连接：${iface.ifName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.size(3.dp))
                    val rows = listOf(
                        "IP 地址" to (iface.currentIp.ifBlank { "—" }),
                        "网关" to (iface.currentGateway.ifBlank { "—" }),
                        "DNS" to (iface.currentDns.ifBlank { "—" })
                    )
                    rows.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = k,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(54.dp),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = v,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (iface.ifName.isBlank()) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "👉 请先连接 Wi-Fi，再点右上角 ↻ 刷新一下。",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningOrange
                        )
                    }
                }
            }
        }
    }
}

// =====================================================
//  2. 模式选择（两个大卡片，小白一眼懂）
// =====================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelectorBig(
    uiState: WifiConfigUiState,
    onModeChanged: (ConfigMode) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 左边：DHCP 自动
        ModeCard(
            modifier = Modifier.weight(1f),
            selected = uiState.mode == ConfigMode.DHCP,
            onClick = { onModeChanged(ConfigMode.DHCP) },
            icon = Icons.Default.Autorenew,
            iconBg = SuccessGreen.copy(alpha = 0.18f),
            iconTint = SuccessGreen,
            title = "自动模式",
            subtitleEn = "DHCP · 推荐",
            description = "路由器自动帮你配好，最省心"
        )
        // 右边：静态手动
        ModeCard(
            modifier = Modifier.weight(1f),
            selected = uiState.mode == ConfigMode.STATIC,
            onClick = { onModeChanged(ConfigMode.STATIC) },
            icon = Icons.Default.SettingsEthernet,
            iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            iconTint = MaterialTheme.colorScheme.primary,
            title = "手动模式",
            subtitleEn = "静态 IP",
            description = "自己填 IP / 网关 / DNS"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeCard(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitleEn: String,
    description: String
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitleEn,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selected) {
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "当前选择",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// =====================================================
//  3a. DHCP 模式下的提示卡片
// =====================================================
@Composable
private fun DhcpHintCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = SuccessGreen.copy(alpha = 0.08f)
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SuccessGreen.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Autorenew, null, tint = SuccessGreen)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "选择了「自动模式」",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "点下面绿色大按钮，就能一键切回路由器自动分配的网络，什么都不用填。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =====================================================
//  3b. 静态表单（带图标分组）
// =====================================================
@Composable
private fun StaticFormCard(
    uiState: WifiConfigUiState,
    onIp: (String) -> Unit,
    onSubnet: (String) -> Unit,
    onGateway: (String) -> Unit,
    onDnsPrimary: (String) -> Unit,
    onDnsSecondary: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 标题栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SettingsEthernet, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        text = "填写 IP / 网关 / DNS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "带 * 的项必填；不会填可以先点上面的「自动模式」",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ---- 第 1 组：IP 地址 + 前缀 ----
            SectionLabel(title = "① IP 地址", hint = "前缀一般填 24（= 子网掩码 255.255.255.0）")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = uiState.ip,
                    onValueChange = onIp,
                    label = {
                        Row { Text("IP 地址"); Star() }
                    },
                    placeholder = { Text("例: 192.168.1.100") },
                    modifier = Modifier.weight(3f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = uiState.subnetPrefix,
                    onValueChange = onSubnet,
                    label = { Text("前缀") },
                    placeholder = { Text("24") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // ---- 第 2 组：网关 ----
            SectionLabel(title = "② 网关 Gateway", hint = "一般就是路由器管理页 IP")
            OutlinedTextField(
                value = uiState.gateway,
                onValueChange = onGateway,
                label = {
                    Row { Text("网关地址"); Star() }
                },
                placeholder = { Text("例: 192.168.1.1  或  192.168.0.1  或  192.168.31.1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // ---- 第 3 组：DNS ----
            SectionLabel(title = "③ DNS", hint = "主 DNS 必需；备用不填也行")
            OutlinedTextField(
                value = uiState.dnsPrimary,
                onValueChange = onDnsPrimary,
                label = {
                    Row { Text("主 DNS"); Star() }
                },
                placeholder = { Text("国内推荐 223.5.5.5(阿里) / 114.114.114.114  国外 8.8.8.8") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = uiState.dnsSecondary,
                onValueChange = onDnsSecondary,
                label = { Text("备用 DNS（可选）") },
                placeholder = { Text("例: 223.6.6.6 / 8.8.4.4") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun Star() {
    Text(" *", color = ErrorRed, fontWeight = FontWeight.Bold)
}

@Composable
private fun SectionLabel(title: String, hint: String = "") {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (hint.isNotBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =====================================================
//  4. 底部主操作区（保存 + 应用）
// =====================================================
@Composable
private fun MainCtaBar(
    uiState: WifiConfigUiState,
    onSave: () -> Unit,
    onApply: () -> Unit
) {
    val busy = uiState.status is OperationStatus.Applying ||
            uiState.status is OperationStatus.CheckingRoot ||
            uiState.status is OperationStatus.LoadingInterface

    val applyLabel = when (uiState.mode) {
        ConfigMode.STATIC -> "立即应用设置"
        ConfigMode.DHCP -> "一键切回自动模式"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 只有静态模式才显示「保存」按钮（次要）
            if (uiState.mode == ConfigMode.STATIC) {
                FilledTonalButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        vertical = 14.dp, horizontal = 10.dp
                    )
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.size(6.dp))
                    Text("保存设置", fontWeight = FontWeight.SemiBold)
                }
            }
            // 主按钮：应用 / 切换
            Button(
                onClick = onApply,
                modifier = Modifier.weight(if (uiState.mode == ConfigMode.STATIC) 1f else 1f),
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    contentColor = Color.White
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    vertical = 14.dp, horizontal = 10.dp
                )
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.size(6.dp))
                } else {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    if (busy) "处理中，请稍等…" else applyLabel,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =====================================================
//  5. 状态横幅（成功 / 失败 / 进度）
// =====================================================
private data class BannerInfo(
    val tint: Color,
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val showProgress: Boolean
)

@Composable
private fun StatusBanner(uiState: WifiConfigUiState) {
    val raw: BannerInfo? = when (val s = uiState.status) {
        OperationStatus.Idle -> {
            val msg = uiState.statusMessage
            if (msg.isBlank()) null
            else BannerInfo(
                MaterialTheme.colorScheme.onSurfaceVariant,
                msg, Icons.Default.Info, false
            )
        }
        is OperationStatus.CheckingRoot,
        OperationStatus.LoadingInterface,
        OperationStatus.Applying -> BannerInfo(
            MaterialTheme.colorScheme.primary,
            uiState.statusMessage.ifBlank { "处理中…" },
            Icons.Default.Refresh, true
        )
        is OperationStatus.RootGranted,
        is OperationStatus.Success -> BannerInfo(
            SuccessGreen,
            uiState.statusMessage.ifBlank { "成功 ✓" },
            Icons.Default.CheckCircle, false
        )
        is OperationStatus.RootDenied,
        is OperationStatus.Failed -> BannerInfo(
            ErrorRed,
            uiState.statusMessage.ifBlank { "失败了" },
            Icons.Default.Warning, false
        )
        is OperationStatus.Info -> BannerInfo(
            WarningOrange, uiState.statusMessage, Icons.Default.Info, false
        )
    }
    val info = raw ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = info.tint.copy(alpha = 0.10f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            if (info.showProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = info.tint,
                    trackColor = info.tint.copy(alpha = 0.15f)
                )
                Spacer(Modifier.size(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(info.icon, null, tint = info.tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = info.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = info.tint,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// =====================================================
//  6. 诊断日志（默认收起）
//  小白看不懂，所以只在失败时给醒目的红色提示，默认折叠
// =====================================================
@Composable
private fun DiagnosticsCard(uiState: WifiConfigUiState) {
    var expanded by remember { mutableStateOf(false) }
    val diags = uiState.lastDiagnostics
    val failedCount = diags.count { !it.ok }
    val totalCount = diags.size
    val allOk = failedCount == 0

    // 只有当展开或存在失败时才显示；全部成功默认折叠
    AnimatedVisibility(
        visible = expanded || failedCount > 0,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (allOk) MaterialTheme.colorScheme.outlineVariant else ErrorRed.copy(alpha = 0.4f)
            )
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (allOk) SuccessGreen.copy(alpha = 0.15f)
                                else ErrorRed.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (allOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (allOk) SuccessGreen else ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (allOk) "命令执行全部成功 ✓"
                            else "有 $failedCount 条命令失败了（共 $totalCount 条）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (allOk) SuccessGreen else ErrorRed
                        )
                        Text(
                            text = if (allOk)
                                "网络改好了～如果没有生效，可以开关一下飞行模式再看。"
                            else
                                "可以点开下面的详细日志，把截图发给开发者排查问题。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val rotate by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        label = "rotate",
                    )
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            Icons.Default.ExpandMore,
                            if (expanded) "收起" else "展开详情",
                            modifier = Modifier.rotate(rotate)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Divider(Modifier.padding(vertical = 10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            diags.forEach { d ->
                                Text(
                                    text = d.toDisplayLine(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (d.ok)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        ErrorRed,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (d.ok) Color.Transparent
                                            else ErrorRed.copy(alpha = 0.06f)
                                        )
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================
//  预设方案卡片 & 对话框（重做视觉，chips 清爽化）
// =====================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetsCard(
    uiState: WifiConfigUiState,
    onCreate: () -> Unit,
    onRename: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onClearAll: () -> Unit,
    onLoad: (id: String) -> Unit
) {
    val presets = uiState.presets
    val max = com.wificonfig.app.data.SavedPreset.MAX_PRESETS

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 顶栏
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Folder, null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "我的预设方案  ${presets.size}/$max",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "保存多套常用配置，一键切换。最多 $max 套。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.size(4.dp))
                FilledTonalIconButton(onClick = onClearAll, enabled = presets.isNotEmpty()) {
                    Icon(Icons.Default.ClearAll, contentDescription = "清空全部")
                }
                FilledTonalButton(
                    onClick = onCreate,
                    enabled = presets.size < max,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("另存为预设")
                }
            }

            if (presets.isEmpty()) {
                EmptyPresetsHint()
            } else {
                // 预设方案：竖向列表（每行一个，方便逐行查看）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        val selected = uiState.selectedPresetId == p.id
                        PresetItem(
                            preset = p,
                            selected = selected,
                            onClick = { onLoad(p.id) },
                            onRename = { onRename(p.id) },
                            onDelete = { onDelete(p.id) }
                        )
                    }
                    Text(
                        text = "👆 点一下某一行 = 自动填入上面表单；✏️铅笔 = 改名；🗑️垃圾桶 = 删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPresetsHint() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Info, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = "还没有预设 —— 填好上面 5 个字段后，点「另存为预设」就能保存啦。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetItem(
    preset: com.wificonfig.app.data.SavedPreset,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：图标
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) SuccessGreen.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (selected) SuccessGreen else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
            // 中：名称 + 一行摘要
            Column(Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = preset.toOneLineSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                if (selected) {
                    Spacer(Modifier.size(3.dp))
                    Text(
                        text = "✓ 已载入到表单",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            // 右：重命名 / 删除 两个按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onRename,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Edit, "重命名", modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.size(4.dp))
                FilledTonalIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = ErrorRed.copy(alpha = 0.12f),
                        contentColor = ErrorRed
                    )
                ) {
                    Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ---------- 对话框们（基本保留原逻辑，加圆角润色） ----------

@Composable
private fun PresetCreateDialog(
    onCancel: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
        title = { Text("另存为预设") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("给这套配置起个名字（24 字以内）：")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("预设名称") },
                    placeholder = { Text("如：家里主路由 2.4G / 公司 Wi-Fi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp)
            ) { Text("取消") }
        }
    )
}

@Composable
private fun PresetRenameDialog(
    currentName: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        title = { Text("重命名预设") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text("新名称（24 字以内）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(12.dp)) { Text("取消") }
        }
    )
}

@Composable
private fun PresetDeleteDialog(
    presetName: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
        title = { Text("删除预设") },
        text = { Text("确定删除「$presetName」吗？删了就找不回来啦。") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed,
                    contentColor = Color.White
                )
            ) { Text("确定删除") }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(12.dp)) { Text("取消") }
        }
    )
}

@Composable
private fun PresetClearAllDialog(
    count: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.ClearAll, contentDescription = null, tint = WarningOrange) },
        title = { Text("清空全部预设") },
        text = { Text("真的要删除全部 $count 套预设吗？此操作不可恢复哦。") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarningOrange,
                    contentColor = Color.White
                )
            ) { Text("全部清空") }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(12.dp)) { Text("取消") }
        }
    )
}

// =====================================================
//  预览（浅色 / 深色）
// =====================================================
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewScreen() {
    WifiConfigAppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            // 仅用于预览画布
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    "🎨 UI 预览占位\n安装到手机上就能看到完整效果啦～",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
