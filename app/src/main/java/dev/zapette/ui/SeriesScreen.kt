package dev.zapette.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import dev.zapette.R
import dev.zapette.data.Entry
import dev.zapette.data.Episode
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesScreen(vm: BrowseViewModel, series: Entry, onPlay: () -> Unit) {
    LaunchedEffect(series.id) { vm.loadSeries(series) }
    val episodesFocus = remember { FocusRequester() }
    val detailState = if (vm.seriesLoadedId == series.id) vm.seriesDetail else Load.Loading

    Row(
        Modifier
            .fillMaxSize()
            .background(ZColors.Bg)
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Column(Modifier.width(250.dp).fillMaxHeight()) {
            val detail = (detailState as? Load.Ok)?.value
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ZColors.Placeholder),
            ) {
                val cover = series.image ?: detail?.cover
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(series.name, color = ZColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            listOfNotNull(detail?.genre, detail?.releaseDate?.take(4)).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, color = ZColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            (detail?.plot ?: series.plot)?.let {
                Text(
                    it,
                    color = ZColors.TextDim,
                    fontSize = 13.sp,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        Spacer(Modifier.width(32.dp))

        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (val d = detailState) {
                Load.Idle, Load.Loading -> LoadingBox()
                is Load.Err -> MessageBox(d.message, isError = true, onRetry = { vm.loadSeries(series, force = true) })
                is Load.Ok -> {
                    val seasons = d.value.seasons.keys.toList()
                    if (seasons.isEmpty()) {
                        MessageBox(stringResource(R.string.series_no_episodes))
                    } else {
                        var selectedSeason by rememberSaveable(series.id) { mutableStateOf(seasons.first()) }
                        val season = if (selectedSeason in seasons) selectedSeason else seasons.first()
                        val episodes = d.value.seasons[season].orEmpty()

                        LaunchedEffect(d) { episodesFocus.requestWhenReady() }

                        Column(Modifier.fillMaxSize()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(4.dp),
                            ) {
                                items(seasons) { s ->
                                    Chip(stringResource(R.string.season_n, s), selected = s == season, onClick = { selectedSeason = s })
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            val tick = vm.resumeTick
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(episodesFocus)
                                    .focusRestorer(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(6.dp),
                            ) {
                                itemsIndexed(episodes, key = { _, ep -> ep.id }) { index, ep ->
                                    val resume = remember(ep.id, tick) {
                                        vm.resumePosition(BrowseViewModel.episodeKey(ep.id))
                                    }
                                    EpisodeRow(ep, resume) {
                                        vm.playEpisodes(episodes, index)
                                        onPlay()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, resumeMs: Long, onClick: () -> Unit) {
    TvCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(84.dp),
        focusScale = 1.02f,
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ZColors.Placeholder),
                contentAlignment = Alignment.Center,
            ) {
                if (ep.image != null) {
                    AsyncImage(
                        model = ep.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("E${ep.number}", color = ZColors.TextDim, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${ep.number}. ${ep.title}",
                    color = if (focused) ZColors.Text else ZColors.TextDim,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val resumeLabel = stringResource(R.string.resume_at, formatShort(resumeMs))
                val meta = buildList {
                    ep.duration?.let { add(it) }
                    if (resumeMs > 0) add(resumeLabel)
                }.joinToString("  ·  ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        color = if (resumeMs > 0) ZColors.Accent else ZColors.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                ep.plot?.let {
                    Text(
                        it,
                        color = ZColors.TextDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun formatShort(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}
