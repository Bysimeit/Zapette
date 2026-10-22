package dev.zapette.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.zapette.R
import dev.zapette.data.Entry
import java.util.Locale

@Composable
fun MovieScreen(vm: BrowseViewModel, movie: Entry, onPlay: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(movie.id) { vm.loadMovie(movie) }
    val isTv = LocalIsTv.current
    val playFocus = remember { FocusRequester() }
    val detailState = if (vm.movieLoadedId == movie.id) vm.movieDetail else Load.Loading
    val detail = (detailState as? Load.Ok)?.value
    val resume = remember(movie.id, vm.resumeTick) { vm.resumePosition(BrowseViewModel.movieKey(movie.id)) }

    LaunchedEffect(Unit) { if (isTv) playFocus.requestWhenReady() }

    val poster: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(ZColors.Placeholder),
        ) {
            val cover = movie.image ?: detail?.cover
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    val header: @Composable () -> Unit = {
        Text(movie.name, color = ZColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3)
        val rating = movie.rating?.toDoubleOrNull()?.takeIf { it > 0 }
            ?.let { String.format(Locale.getDefault(), "★ %.1f", it) }
        listOfNotNull(
            detail?.genre,
            detail?.releaseDate?.let { YEAR.find(it)?.value },
            detail?.minutes?.let(::formatRuntime),
            rating,
        ).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
            Text(it, color = ZColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }

    val playButton: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            ActionButton(
                text = if (resume > 0) stringResource(R.string.resume_at, formatShort(resume)) else stringResource(R.string.play),
                onClick = {
                    vm.playMovie(movie)
                    onPlay()
                },
                modifier = Modifier.fillMaxWidth().focusRequester(playFocus),
            )
            if (resume > 0) {
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = stringResource(R.string.restart),
                    onClick = {
                        vm.prefs.clearPosition(BrowseViewModel.movieKey(movie.id))
                        vm.playMovie(movie)
                        onPlay()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    val details: @Composable () -> Unit = {
        when (val d = detailState) {
            Load.Idle, Load.Loading -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ZColors.Accent, modifier = Modifier.size(28.dp))
            }
            is Load.Err -> Column {
                Text(d.message, color = ZColors.Error, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                ActionButton(stringResource(R.string.retry), onClick = { vm.loadMovie(movie, force = true) })
            }
            is Load.Ok -> Column {
                (d.value.plot ?: movie.plot)?.let {
                    Text(
                        it,
                        color = ZColors.Text,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        maxLines = if (isTv) 8 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                d.value.director?.let {
                    Text(
                        stringResource(R.string.movie_director, it),
                        color = ZColors.TextDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                d.value.cast?.let {
                    Text(
                        stringResource(R.string.movie_cast, it),
                        color = ZColors.TextDim,
                        fontSize = 13.sp,
                        maxLines = if (isTv) 2 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    if (isCompact()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(ZColors.Bg)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            PhoneBackButton(onBack)
            Row {
                poster(Modifier.width(120.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { header() }
            }
            Spacer(Modifier.height(16.dp))
            playButton(Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            details()
        }
        return
    }

    val short = isShort()
    Row(
        Modifier
            .fillMaxSize()
            .background(ZColors.Bg)
            .padding(horizontal = if (short) 16.dp else 40.dp, vertical = if (short) 10.dp else 28.dp),
    ) {
        Column(
            Modifier
                .width(if (short) 170.dp else 250.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            PhoneBackButton(onBack)
            poster(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(if (short) 20.dp else 32.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            header()
            Spacer(Modifier.height(20.dp))
            playButton(Modifier.width(IntrinsicSize.Max))
            Spacer(Modifier.height(20.dp))
            details()
        }
    }
}

private val YEAR = Regex("""\b(19|20)\d{2}\b""")

private fun formatRuntime(minutes: Int): String =
    if (minutes < 60) "$minutes min" else String.format(Locale.ROOT, "%dh%02d", minutes / 60, minutes % 60)
