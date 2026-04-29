package com.xnigma.xnigma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.xnigma.xnigma.ui.AppNavigation
import com.xnigma.xnigma.ui.theme.VirtualChatLockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VirtualChatLockTheme {
                AppNavigation()
            }
        }
    }
}
