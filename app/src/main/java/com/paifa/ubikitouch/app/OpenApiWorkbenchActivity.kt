package com.paifa.ubikitouch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.paifa.ubikitouch.accessibility.FloatingChatOpenApiBridge

class OpenApiWorkbenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 兼容旧 Activity 深链：实际页面必须回到 UI组件 同一聊天根，禁止附加第二个 Window。
        // 测试流程：从旧入口启动后，确认立即转为已有浮窗工作区且没有白色 Activity 页面。
        FloatingChatOpenApiBridge.open()
        finish()
    }
}
