package com.xfin.app

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.os.Build
import com.xfin.feature.player.PipHost
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xfin.app.navigation.XfinNavHost
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.designsystem.theme.XfinTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PipHost {

    /** Set by the player on screen; used to enter PiP on Home before Android 12. */
    override var pipParams: PictureInPictureParams? = null

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) pipParams?.let { runCatching { enterPictureInPictureMode(it) } }
    }


    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.sources.value == null }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val isTv = getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        setContent {
            val sources by viewModel.sources.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalIsTv provides isTv) {
                XfinTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        sources?.let { XfinNavHost(sources = it) }
                    }
                }
            }
        }
    }
}
