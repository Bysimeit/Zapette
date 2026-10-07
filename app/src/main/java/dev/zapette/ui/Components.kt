package dev.zapette.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.zapette.R

@Composable
fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    background: Color = ZColors.Surface,
    focusedBackground: Color = ZColors.SurfaceFocused,
    focusScale: Float = 1.06f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) focusedBackground else background)
            .then(if (focused) Modifier.border(2.5.dp, ZColors.Accent, shape) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange?.invoke(it.isFocused)
            }
            .onPreviewKeyEvent { e ->
                val isSelect = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (!isSelect) return@onPreviewKeyEvent false
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        val repeat = e.nativeKeyEvent.repeatCount
                        if (repeat == 0) {
                            longFired = false
                        } else if (!longFired && longClick != null) {
                            longFired = true
                            longClick?.invoke()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (!longFired) click()
                        longFired = false
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { click() },
                    onLongPress = { longClick?.invoke() },
                )
            },
    ) {
        content(focused)
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        background = if (selected) ZColors.Surface else Color.Transparent,
        focusScale = 1.08f,
    ) { focused ->
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            color = when {
                selected -> ZColors.Accent
                focused -> ZColors.Text
                else -> ZColors.TextDim
            },
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    TvCard(
        onClick = onClick,
        modifier = modifier,
        background = ZColors.Surface,
        focusedBackground = if (danger) ZColors.Error else ZColors.Accent,
        focusScale = 1.04f,
    ) { focused ->
        Text(
            text = text,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            color = when {
                focused -> Color.Black
                danger -> ZColors.Error
                else -> ZColors.Text
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ZColors.Accent)
    }
}

@Composable
fun MessageBox(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = message,
                color = if (isError) ZColors.Error else ZColors.TextDim,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 460.dp),
            )
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                ActionButton(stringResource(R.string.retry), onRetry)
            }
        }
    }
}

suspend fun FocusRequester.requestWhenReady() {
    repeat(10) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.isSuccess) return
    }
}
