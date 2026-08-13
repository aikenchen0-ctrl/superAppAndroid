package com.paifa.ubikitouch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import com.paifa.ubikitouch.accessibility.floatingchat.moments.MaterialLibraryActivityContent

class MaterialLibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler(onBack = ::finish)
            MaterialLibraryActivityContent(applicationContext, ::finish)
        }
    }
}
