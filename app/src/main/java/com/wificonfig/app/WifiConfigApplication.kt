package com.wificonfig.app

import android.app.Application

/**
 * 全局 Application 入口
 * 暂时保持简单，后续可在此做全局初始化（如日志、Crash 监控等）
 */
class WifiConfigApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 预留：可以在此做一次性初始化
    }
}
