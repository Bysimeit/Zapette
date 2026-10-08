package dev.zapette.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.zapette.R
import dev.zapette.BuildConfig
import dev.zapette.data.Kind
import dev.zapette.data.LiveFormat
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchPanel(
    vm: BrowseViewModel,
    onOpenSeries: (dev.zapette.data.Entry) -> Unit,
    onPlay: () -> Unit,
    gridFocus: FocusRequester,
) {
    val compact = isCompact()
    val field: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = vm::onSearchChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint), color = ZColors.TextDim) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ZColors.Accent,
                cursorColor = ZColors.Accent,
            ),
            modifier = modifier,
        )
    }
    val kinds: @Composable () -> Unit = {
        if (vm.kinds.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.kinds.forEach { k ->
                Chip(stringResource(k.label), selected = vm.searchKind == k, onClick = { vm.updateSearchKind(k) })
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (compact) {
            field(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            kinds()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                field(Modifier.width(460.dp))
                Spacer(Modifier.width(16.dp))
                kinds()
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxSize()) {
            when (val results = vm.searchResults) {
                Load.Idle -> MessageBox(stringResource(R.string.search_idle))
                Load.Loading -> LoadingBox()
                is Load.Err -> MessageBox(results.message, isError = true)
                is Load.Ok -> if (results.value.isEmpty()) {
                    MessageBox(stringResource(R.string.search_no_results))
                } else {
                    EntryGrid(vm, vm.searchKind, results.value, onOpenSeries, onPlay, gridFocus)
                }
            }
        }
    }
}

@Composable
fun SettingsPanel(vm: BrowseViewModel, onLoggedOut: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadAccountInfo() }
    val account = vm.prefs.account
    val activity = LocalActivity.current
    val language = remember(activity) { activity?.let { AppLanguage.current(it) } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
    ) {
        Section(stringResource(R.string.section_subscription))
        val isPlaylist = account?.isPlaylist == true
        InfoLine(stringResource(if (isPlaylist) R.string.label_playlist else R.string.label_server), account?.server ?: "-")
        if (!isPlaylist) InfoLine(stringResource(R.string.label_user), account?.username ?: "-")
        if (!isPlaylist) when (val info = vm.accountInfo) {
            Load.Idle, Load.Loading -> InfoLine(stringResource(R.string.label_status), stringResource(R.string.status_loading))
            is Load.Err -> InfoLine(stringResource(R.string.label_status), info.message)
            is Load.Ok -> info.value?.let { i ->
                InfoLine(
                    stringResource(R.string.label_status),
                    if (i.isTrial) stringResource(R.string.status_trial, i.status) else i.status,
                )
                InfoLine(
                    stringResource(R.string.label_expiration),
                    i.expiresAt?.let {
                        DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(Date(it * 1000))
                    } ?: stringResource(R.string.expiration_unlimited),
                )
                InfoLine(stringResource(R.string.label_connections), "${i.activeConnections ?: "?"} / ${i.maxConnections ?: "?"}")
            }
        }

        Section(stringResource(R.string.section_language))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(
                stringResource(R.string.language_system),
                selected = language == null,
                onClick = { activity?.let { AppLanguage.set(it, null) } },
            )
            AppLanguage.tags.forEach { tag ->
                Chip(
                    AppLanguage.label(tag),
                    selected = language == tag,
                    onClick = { activity?.let { AppLanguage.set(it, tag) } },
                )
            }
        }

        if (!isPlaylist) {
            Section(stringResource(R.string.section_live_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveFormat.entries.forEach { f ->
                    Chip(f.label, selected = vm.liveFormat == f, onClick = { vm.updateLiveFormat(f) })
                }
            }
            Text(
                stringResource(R.string.live_format_help),
                color = ZColors.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Section(stringResource(R.string.section_user_agent))
        OutlinedTextField(
            value = vm.userAgent,
            onValueChange = vm::updateUserAgent,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ZColors.Accent,
                cursorColor = ZColors.Accent,
            ),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        )
        Text(
            stringResource(R.string.user_agent_help),
            color = ZColors.TextDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        Section(stringResource(R.string.section_data))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(stringResource(R.string.reload_lists), onClick = { vm.clearCache() })
            ActionButton(
                stringResource(R.string.sign_out),
                danger = true,
                onClick = {
                    vm.logout()
                    onLoggedOut()
                },
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.about, BuildConfig.VERSION_NAME),
            color = ZColors.TextDim,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = ZColors.Accent,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = ZColors.TextDim, fontSize = 15.sp, modifier = Modifier.width(if (isCompact()) 110.dp else 140.dp))
        Text(value, color = ZColors.Text, fontSize = 15.sp)
    }
}
