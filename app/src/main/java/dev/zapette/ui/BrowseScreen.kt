package dev.zapette.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import dev.zapette.R
import dev.zapette.data.Category
import dev.zapette.data.Entry
import dev.zapette.data.Kind
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun BrowseScreen(
    vm: BrowseViewModel,
    onOpenSeries: (Entry) -> Unit,
    onPlay: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val isTv = LocalIsTv.current
    val compact = isCompact()
    val tabFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!isTv) return@LaunchedEffect
        if (vm.focusGridOnReturn) {
            vm.focusGridOnReturn = false
            gridFocus.requestWhenReady()
        } else {
            tabFocus.requestWhenReady()
        }
    }

    val openSeries: (Entry) -> Unit = {
        vm.focusGridOnReturn = true
        onOpenSeries(it)
    }
    val content: @Composable () -> Unit = {
        when (val t = vm.tab) {
            Tab.SEARCH -> SearchPanel(vm, onOpenSeries = openSeries, onPlay = onPlay, gridFocus = gridFocus)
            Tab.SETTINGS -> SettingsPanel(vm, onLoggedOut = onLoggedOut)
            else -> key(t.kind) {
                KindPanel(vm, t.kind!!, onOpenSeries = openSeries, onPlay = onPlay, gridFocus = gridFocus)
            }
        }
    }

    if (compact) {
        Column(Modifier.fillMaxSize().background(ZColors.Bg)) {
            Text(
                "Zapette",
                color = ZColors.Accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp),
            )
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) { content() }
            BottomTabBar(vm)
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ZColors.Bg)
            .padding(horizontal = 36.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Zapette", color = ZColors.Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tab.entries.forEach { t ->
                    Chip(
                        text = stringResource(t.label),
                        selected = vm.tab == t,
                        onClick = { vm.tab = t },
                        modifier = if (vm.tab == t) Modifier.focusRequester(tabFocus) else Modifier,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

private val Tab.icon: ImageVector
    get() = when (this) {
        Tab.LIVE -> ZIcons.Live
        Tab.MOVIE -> ZIcons.Movies
        Tab.SERIES -> ZIcons.Series
        Tab.SEARCH -> ZIcons.Search
        Tab.SETTINGS -> ZIcons.Settings
    }

@Composable
private fun BottomTabBar(vm: BrowseViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ZColors.Surface)
            .padding(vertical = 4.dp),
    ) {
        Tab.entries.forEach { t ->
            val color = if (vm.tab == t) ZColors.Accent else ZColors.TextDim
            Column(
                Modifier
                    .weight(1f)
                    .clickable { vm.tab = t }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(3.dp))
                BasicText(
                    stringResource(t.label),
                    style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun KindPanel(
    vm: BrowseViewModel,
    kind: Kind,
    onOpenSeries: (Entry) -> Unit,
    onPlay: () -> Unit,
    gridFocus: FocusRequester,
) {
    val st = vm.state(kind)
    LaunchedEffect(kind, vm.api, vm.catalogVersion) { vm.ensureCategories(kind) }

    val itemsArea: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            when (val items = st.items) {
                Load.Idle -> MessageBox(stringResource(R.string.pick_category))
                Load.Loading -> LoadingBox()
                is Load.Err -> MessageBox(items.message, isError = true, onRetry = {
                    st.selected?.let { vm.selectCategory(kind, it, force = true) }
                })
                is Load.Ok -> if (items.value.isEmpty()) {
                    MessageBox(
                        stringResource(
                            if (st.selected == CAT_FAVORITES) R.string.favorites_empty else R.string.category_empty
                        )
                    )
                } else {
                    EntryGrid(vm, kind, items.value, onOpenSeries, onPlay, gridFocus)
                }
            }
        }
    }

    if (isCompact()) {
        Column(Modifier.fillMaxSize()) {
            when (val cats = st.categories) {
                Load.Idle, Load.Loading -> LoadingBox(Modifier.height(56.dp))
                is Load.Err -> MessageBox(
                    cats.message,
                    Modifier.height(160.dp),
                    isError = true,
                    onRetry = { vm.retryCategories(kind) },
                )
                is Load.Ok -> {
                    val all = categoryList(cats.value)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(all, key = { it.id }) { cat ->
                            Chip(cat.name, selected = st.selected == cat.id, onClick = { vm.selectCategory(kind, cat.id) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            itemsArea(Modifier.weight(1f).fillMaxWidth())
        }
        return
    }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(260.dp).fillMaxHeight()) {
            when (val cats = st.categories) {
                Load.Idle, Load.Loading -> LoadingBox()
                is Load.Err -> MessageBox(cats.message, isError = true, onRetry = { vm.retryCategories(kind) })
                is Load.Ok -> {
                    val all = categoryList(cats.value)
                    var pending by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(pending) {
                        val id = pending ?: return@LaunchedEffect
                        delay(350)
                        vm.selectCategory(kind, id)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(all, key = { it.id }) { cat ->
                            CategoryRow(
                                name = cat.name,
                                selected = st.selected == cat.id,
                                onFocused = { pending = cat.id },
                                onClick = { vm.selectCategory(kind, cat.id) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        itemsArea(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun categoryList(categories: List<Category>): List<Category> {
    val favoritesLabel = stringResource(R.string.category_favorites)
    val allLabel = stringResource(R.string.category_all)
    return remember(categories, favoritesLabel, allLabel) {
        listOf(Category(CAT_FAVORITES, favoritesLabel), Category(CAT_ALL, allLabel)) + categories
    }
}

@Composable
private fun CategoryRow(name: String, selected: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    TvCard(
        onClick = onClick,
        onFocusChange = { if (it) onFocused() },
        modifier = Modifier.fillMaxWidth(),
        background = if (selected) ZColors.Surface else Color.Transparent,
        focusScale = 1.03f,
    ) { focused ->
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = when {
                selected -> ZColors.Accent
                focused -> ZColors.Text
                else -> ZColors.TextDim
            },
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EntryGrid(
    vm: BrowseViewModel,
    kind: Kind,
    entries: List<Entry>,
    onOpenSeries: (Entry) -> Unit,
    onPlay: () -> Unit,
    gridFocus: FocusRequester,
) {
    val context = LocalContext.current
    val toggleFavorite: (Entry) -> Unit = { e ->
        val added = vm.toggleFavorite(e)
        Toast.makeText(
            context,
            context.getString(if (added) R.string.favorite_added else R.string.favorite_removed),
            Toast.LENGTH_SHORT,
        ).show()
    }

    val compact = isCompact() || isShort()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(
            when {
                kind == Kind.LIVE -> if (compact) 140.dp else 150.dp
                compact -> 100.dp
                else -> 128.dp
            }
        ),
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(gridFocus)
            .focusRestorer(),
        contentPadding = PaddingValues(if (compact) 4.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        itemsIndexed(entries, key = { _, e -> e.id }) { index, e ->
            val favorite = vm.isFavorite(e)
            when (kind) {
                Kind.LIVE -> ChannelCard(
                    entry = e,
                    favorite = favorite,
                    onClick = {
                        vm.playLive(entries, index)
                        onPlay()
                    },
                    onLongClick = { toggleFavorite(e) },
                )
                Kind.MOVIE -> PosterCard(
                    entry = e,
                    favorite = favorite,
                    onClick = {
                        vm.playMovie(e)
                        onPlay()
                    },
                    onLongClick = { toggleFavorite(e) },
                )
                Kind.SERIES -> PosterCard(
                    entry = e,
                    favorite = favorite,
                    onClick = { onOpenSeries(e) },
                    onLongClick = { toggleFavorite(e) },
                )
            }
        }
    }
}

@Composable
private fun ChannelCard(entry: Entry, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    TvCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth().height(120.dp),
    ) { focused ->
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (entry.image != null) {
                    AsyncImage(
                        model = entry.image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        entry.name.take(3).uppercase(),
                        color = ZColors.TextDim,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = (if (entry.num > 0) "${entry.num}  " else "") + entry.name,
                color = if (focused) ZColors.Text else ZColors.TextDim,
                fontSize = 13.sp,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (favorite) FavoriteBadge(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun PosterCard(entry: Entry, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    TvCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth(),
    ) { focused ->
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(ZColors.Placeholder),
            ) {
                if (entry.image != null) {
                    AsyncImage(
                        model = entry.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        entry.name,
                        modifier = Modifier.align(Alignment.Center).padding(10.dp),
                        color = ZColors.TextDim,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                entry.rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { r ->
                    Text(
                        String.format(Locale.getDefault(), "★ %.1f", r),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = ZColors.Text,
                        fontSize = 11.sp,
                    )
                }
                if (favorite) FavoriteBadge(Modifier.align(Alignment.TopEnd))
            }
            Text(
                text = entry.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = if (focused) ZColors.Text else ZColors.TextDim,
                fontSize = 12.sp,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(5.dp)
            .background(Brush.radialGradient(listOf(Color(0xAA000000), Color.Transparent)), RoundedCornerShape(50)),
    ) {
        Text("★", color = ZColors.Accent, fontSize = 16.sp, modifier = Modifier.padding(3.dp))
    }
}
