package com.paifa.ubikitouch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import com.paifa.ubikitouch.accessibility.floatingchat.scrm.OpenApiWorkbenchActivityContent

class OpenApiWorkbenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler(onBack = ::finish)
            OpenApiWorkbenchActivityContent(applicationContext, ::finish)
        }
    }
}
