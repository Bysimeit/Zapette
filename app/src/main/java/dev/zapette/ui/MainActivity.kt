package dev.zapette.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.zapette.data.Entry
import dev.zapette.player.PlayerActivity

class MainActivity : ComponentActivity() {

    private val vm: BrowseViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZapetteTheme {
                ZapetteApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResumed()
    }
}

private sealed interface Screen {
    data object Login : Screen
    data object Browse : Screen
    data class Series(val entry: Entry) : Screen
}

@Composable
private fun ZapetteApp(vm: BrowseViewModel) {
    val context = LocalContext.current
    var stack by remember { mutableStateOf(listOf(if (vm.api != null) Screen.Browse else Screen.Login)) }
    val saveableState = rememberSaveableStateHolder()

    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    val play: () -> Unit = { context.openPlayer() }

    Box(Modifier.fillMaxSize().background(ZColors.Bg)) {
        when (val screen = stack.last()) {
            Screen.Login -> LoginScreen(vm, onLoggedIn = { stack = listOf(Screen.Browse) })
            Screen.Browse -> saveableState.SaveableStateProvider("browse") {
                BrowseScreen(
                    vm = vm,
                    onOpenSeries = { stack = stack + Screen.Series(it) },
                    onPlay = play,
                    onLoggedOut = { stack = listOf(Screen.Login) },
                )
            }
            is Screen.Series -> SeriesScreen(vm, screen.entry, onPlay = play)
        }
    }
}

private fun Context.openPlayer() {
    startActivity(Intent(this, PlayerActivity::class.java))
}
