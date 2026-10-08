package dev.zapette.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

val LocalIsTv = staticCompositionLocalOf { false }

fun Context.isTv(): Boolean =
    getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

@Composable
fun isCompact(): Boolean = LocalConfiguration.current.screenWidthDp < 600

@Composable
fun isShort(): Boolean = LocalConfiguration.current.screenHeightDp < 500
