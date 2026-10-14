package com.xfin.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.model.MediaItem
import com.xfin.core.model.SourceType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AUTO_ADVANCE_MS = 8_000L

/** Picks the banner's items: recently added titles with artwork, mixed across libraries. */
internal fun heroItems(servers: List<ServerHome>, max: Int = 6): List<MediaItem> {
    // Jellyfin libraries have proper artwork and titles; Xtream catalogues are the fallback.
    val jellyfin = servers.filter { it.server.type == SourceType.JELLYFIN && it.rows.isNotEmpty() }
    val rows = jellyfin.ifEmpty { servers }.flatMap { home ->
        home.rows.filter { it.key.startsWith("latest-") }.map { row -> row.items.filter { it.backdropUrl != null } }
    }
    val picked = ArrayList<MediaItem>(max)
    var i = 0
    // Round-robin over the rows so movies and shows alternate.
    while (picked.size < max && rows.any { it.size > i }) {
        rows.forEach { row -> if (picked.size < max) row.getOrNull(i)?.let(picked::add) }
        i++
    }
    return picked
}

/**
 * Home's banner, like UHF's: a rotating backdrop with the title's logo (or name), its genre,
 * year and rating, and More info. [header] is drawn on top of it.
 */
@Composable
internal fun HomeHero(
    items: List<MediaItem>,
    height: Dp,
    onOpenItem: (MediaItem) -> Unit,
    header: @Composable () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val isTv = LocalIsTv.current
    val background = MaterialTheme.colorScheme.background

    // Keyed on the settled page: currentPage flips halfway through a swipe, which would restart
    // this effect and cancel its own animation, leaving the banner stuck between two slides.
    LaunchedEffect(pager.settledPage, items.size) {
        if (items.size < 2) return@LaunchedEffect
        delay(AUTO_ADVANCE_MS)
        if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.settledPage + 1) % items.size)
    }
    // TV: Home starts on More info, like UHF.
    if (isTv) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val buttonInteraction = remember { MutableInteractionSource() }
    val buttonFocused by buttonInteraction.collectIsFocusedAsState()

    Box(Modifier.fillMaxWidth().height(height)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = items[page].backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Darken the top for the header and fade the bottom into the page.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.25f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to background,
                        ),
                    ),
                )
            }
        }

        val item = items[pager.currentPage.coerceIn(0, items.lastIndex)]
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "New",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            TitleArt(item, isTv)
            val details = listOfNotNull(
                item.genres.take(2).joinToString(", ").ifEmpty { null },
                item.year?.toString(),
                item.communityRating?.let { "★ " + "%.1f".format(it).removeSuffix(".0") },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
            Button(
                onClick = { onOpenItem(item) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                interactionSource = buttonInteraction,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .then(if (isTv) Modifier else Modifier.fillMaxWidth(0.7f))
                    // A white button needs a ring of its own to show focus on TV.
                    .then(if (buttonFocused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp) else Modifier)
                    .onPreviewKeyEvent { event ->
                        // TV: Right flips to the next slide; Left is left free for the sidebar,
                        // so the banner never traps focus.
                        if (event.type != KeyEventType.KeyDown || items.size < 2 || event.key != Key.DirectionRight) {
                            return@onPreviewKeyEvent false
                        }
                        scope.launch { pager.animateScrollToPage((pager.currentPage + 1) % items.size) }
                        true
                    },
            ) {
                Icon(Icons.Outlined.Info, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("More info", style = MaterialTheme.typography.titleMedium)
            }
            if (items.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(items.size) { i ->
                        val current = i == pager.currentPage
                        Box(
                            Modifier
                                .size(if (current) 8.dp else 6.dp)
                                .background(Color.White.copy(alpha = if (current) 0.95f else 0.4f), CircleShape),
                        )
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) { header() }
    }
}

@Composable
private fun TitleArt(item: MediaItem, isTv: Boolean) {
    var logoFailed by remember(item.logoUrl) { mutableStateOf(item.logoUrl == null) }
    if (!logoFailed) {
        AsyncImage(
            model = item.logoUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            onError = { logoFailed = true },
            modifier = Modifier.fillMaxWidth(if (isTv) 0.4f else 0.75f).heightIn(max = if (isTv) 120.dp else 96.dp),
        )
    } else {
        Text(
            item.name,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
