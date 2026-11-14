package app.iptv.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.iptv.player.ui.AppShell
import app.iptv.player.ui.LocalPip
import app.iptv.player.ui.PipController
import app.iptv.player.ui.login.LoginScreen
import app.iptv.player.ui.theme.IptvTheme
import app.iptv.player.ui.theme.Palette

class MainActivity : ComponentActivity() {
    private val pip = PipController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IptvTheme {
                CompositionLocalProvider(LocalPip provides pip) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Palette.Background) {
                        val creds by graph.session.creds.collectAsState()
                        var adding by rememberSaveable { mutableStateOf(false) }
                        BackHandler(enabled = adding) { adding = false }
                        when {
                            creds == null -> LoginScreen(graph, onDone = { adding = false })
                            adding -> LoginScreen(graph, onCancel = { adding = false }, onDone = { adding = false })
                            else -> AppShell(graph, onAddAccount = { adding = true })
                        }
                    }
                }
            }
        }
    }

    /** Al salir de la app (botón de inicio) con un canal en vivo elegido, sigue en una ventana flotante. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pip.canEnter) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip.active = isInPictureInPictureMode
    }
}
