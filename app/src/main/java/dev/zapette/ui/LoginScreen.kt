package dev.zapette.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.zapette.R
import dev.zapette.data.Account
import dev.zapette.data.XtreamApi
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(vm: BrowseViewModel, onLoggedIn: () -> Unit) {
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val firstField = remember { FocusRequester() }

    val missingFields = stringResource(R.string.login_missing_fields)
    val fallbackError = stringResource(R.string.error_connect)

    val isTv = LocalIsTv.current
    LaunchedEffect(Unit) { if (isTv) firstField.requestWhenReady() }

    fun submit() {
        if (loading) return
        if (server.isBlank() || username.isBlank() != password.isBlank()) {
            error = missingFields
            return
        }
        scope.launch {
            loading = true
            error = null
            vm.login(Account(server.trim(), username.trim(), password))
                .onSuccess { onLoggedIn() }
                .onFailure { error = it.message ?: fallbackError }
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().background(ZColors.Bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .background(ZColors.Surface, RoundedCornerShape(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(if (isCompact()) 22.dp else 36.dp),
        ) {
            Text("Zapette", color = ZColors.Accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.login_subtitle),
                color = ZColors.TextDim,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(24.dp))

            LoginField(
                value = server,
                onValueChange = { value ->
                    val parsed = XtreamApi.parseM3uLink(value)
                    if (parsed != null) {
                        server = parsed.server
                        username = parsed.username
                        password = parsed.password
                    } else {
                        server = value
                    }
                },
                label = stringResource(R.string.login_server),
                placeholder = stringResource(R.string.login_server_hint),
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.focusRequester(firstField),
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )
            LoginField(
                value = username,
                onValueChange = { username = it },
                label = stringResource(R.string.login_username),
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            )
            LoginField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.login_password),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                imeAction = ImeAction.Done,
                onNext = { submit() },
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = ZColors.Error, fontSize = 14.sp)
            }
            Spacer(Modifier.height(20.dp))
            ActionButton(
                text = stringResource(if (loading) R.string.login_connecting else R.string.login_button),
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.login_tip_m3u),
                color = ZColors.TextDim,
                fontSize = 12.sp,
            )
            if (isTv) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.login_tip_tv),
                    color = ZColors.TextDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder, color = ZColors.TextDim) }
        } else {
            null
        },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onNext() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ZColors.Accent,
            focusedLabelColor = ZColors.Accent,
            cursorColor = ZColors.Accent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}
