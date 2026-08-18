package com.wificonfig.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.wificonfig.app.ui.WifiConfigScreen
import com.wificonfig.app.ui.theme.WifiConfigAppTheme

/**
 * 单 Activity 架构：Compose 内容直接通过 setContent 挂载
 */
class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String> = buildList {
        add(Manifest.permission.ACCESS_WIFI_STATE)
        add(Manifest.permission.CHANGE_WIFI_STATE)
        add(Manifest.permission.ACCESS_NETWORK_STATE)
        add(Manifest.permission.INTERNET)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 权限结果非必须阻塞流程，授权失败就仅影响部分信息读取
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 运行时申请位置与 Wi-Fi 相关权限（读取当前连接状态需要）
        ensurePermissions()

        setContent {
            WifiConfigAppTheme {
                WifiConfigScreen()
            }
        }
    }

    private fun ensurePermissions() {
        val needRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest.isNotEmpty()) {
            permissionLauncher.launch(needRequest.toTypedArray())
        }
    }
}
