package app.k56.player.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.k56.player.R
import app.k56.player.data.LibraryDisplay
import app.k56.player.data.PlayerBackground
import app.k56.player.data.PlayerStyle
import app.k56.player.data.SettingsStore
import app.k56.player.ui.components.ScreenHeader
import app.k56.player.ui.screens.settings.SettingsSection

/**
 * One place to shape the app's look: a live preview, the home rows, the
 * library grid, list details and the player. Everything writes straight to
 * [SettingsStore], so the screens react while the editor is still open.
 *
 * The structure is K56's own: a preview card, then plain sections. Nothing is
 * borrowed from another player's layout editor.
 */
@Composable
fun LayoutScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val spacing by settingsStore.gridSpacing.collectAsStateWithLifecycle()
    val columns by settingsStore.libraryColumns.collectAsStateWithLifecycle()
    val display by settingsStore.libraryDisplay.collectAsStateWithLifecycle()
    val showFavoriteBadge by settingsStore.showFavoriteBadge.collectAsStateWithLifecycle()
    val showOfflineBadge by settingsStore.showOfflineBadge.collectAsStateWithLifecycle()
    val showNumbers by settingsStore.showTrackNumbers.collectAsStateWithLifecycle()
    val showSubtitle by settingsStore.showTrackSubtitle.collectAsStateWithLifecycle()
    val showItemCount by settingsStore.showItemCount.collectAsStateWithLifecycle()
    val playerStyle by settingsStore.playerStyle.collectAsStateWithLifecycle()
    val playerBackground by settingsStore.playerBackground.collectAsStateWithLifecycle()
    val compactControls by settingsStore.compactControls.collectAsStateWithLifecycle()
    val fitCover by settingsStore.fitCover.collectAsStateWithLifecycle()
    val coverRadius by settingsStore.coverCornerRadius.collectAsStateWithLifecycle()
    val coverMargin by settingsStore.coverMarginDp.collectAsStateWithLifecycle()
    val homeSections by settingsStore.homeSections.collectAsStateWithLifecycle()

    val allSections = SectionOrder
    val enabled = homeSections.filter { it in allSections }
    val ordered = enabled + allSections.filter { it !in enabled }

    fun move(key: String, delta: Int) {
        val list = enabled.toMutableList()
        val i = list.indexOf(key)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
        settingsStore.applyHomeSections(list)
    }

    fun toggle(key: String, on: Boolean) {
        val list = enabled.toMutableList()
        if (on) {
            if (!list.contains(key)) list.add(key)
        } else {
            list.remove(key)
        }
        settingsStore.applyHomeSections(list)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        ScreenHeader(stringResource(R.string.layout_title), onBack = onBack)

        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.layout_preview),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
        )
        LayoutPreview(
            spacing = spacing,
            columns = columns,
            listMode = display == LibraryDisplay.LIST,
            coverRadius = coverRadius,
            coverMargin = coverMargin,
            compactControls = compactControls,
        )

        SettingsSection(title = stringResource(R.string.layout_presets)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip(stringResource(R.string.layout_preset_compact)) {
                    settingsStore.setLibraryColumns(4)
                    settingsStore.setGridSpacing(4)
                }
                PresetChip(stringResource(R.string.layout_preset_standard)) {
                    settingsStore.setLibraryColumns(0)
                    settingsStore.setGridSpacing(12)
                }
                PresetChip(stringResource(R.string.layout_preset_spacious)) {
                    settingsStore.setLibraryColumns(2)
                    settingsStore.setGridSpacing(20)
                }
            }
        }

        SettingsSection(
            title = stringResource(R.string.layout_home_rows),
        ) {
            Text(
                stringResource(R.string.layout_home_rows_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            ordered.forEach { key ->
                val on = key in enabled
                val position = enabled.indexOf(key)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(sectionLabel(key)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (on) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (on) {
                        IconButton(
                            onClick = { move(key, -1) },
                            enabled = position > 0,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.layout_move_up),
                            )
                        }
                        IconButton(
                            onClick = { move(key, 1) },
                            enabled = position in 0 until enabled.size - 1,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.layout_move_down),
                            )
                        }
                    } else {
                        Spacer(Modifier.width(72.dp))
                    }
                    Switch(checked = on, onCheckedChange = { toggle(key, it) })
                }
            }
        }

        SettingsSection(title = stringResource(R.string.layout_library)) {
            Text(stringResource(R.string.layout_mode), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = display == LibraryDisplay.GRID,
                    onClick = { settingsStore.setLibraryDisplay(LibraryDisplay.GRID) },
                    label = { Text(stringResource(R.string.layout_mode_grid)) },
                )
                FilterChip(
                    selected = display == LibraryDisplay.LIST,
                    onClick = { settingsStore.setLibraryDisplay(LibraryDisplay.LIST) },
                    label = { Text(stringResource(R.string.layout_mode_list)) },
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.display_columns), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 2, 3, 4, 5).forEach { value ->
                    FilterChip(
                        selected = columns == value,
                        onClick = { settingsStore.setLibraryColumns(value) },
                        label = {
                            Text(
                                if (value == 0) {
                                    stringResource(R.string.display_columns_auto)
                                } else {
                                    value.toString()
                                },
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.layout_spacing),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.layout_spacing_value, spacing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 8, 12, 16, 24).forEach { value ->
                    FilterChip(
                        selected = spacing == value,
                        onClick = { settingsStore.setGridSpacing(value) },
                        label = { Text("$value") },
                    )
                }
            }
        }

        SettingsSection(title = stringResource(R.string.layout_covers)) {
            LayoutSwitch(
                title = stringResource(R.string.layout_badge_favorite),
                body = stringResource(R.string.layout_badge_favorite_body),
                checked = showFavoriteBadge,
                onChange = { settingsStore.setShowFavoriteBadge(it) },
            )
            LayoutSwitch(
                title = stringResource(R.string.layout_badge_offline),
                body = stringResource(R.string.layout_badge_offline_body),
                checked = showOfflineBadge,
                onChange = { settingsStore.setShowOfflineBadge(it) },
            )
        }

        SettingsSection(title = stringResource(R.string.layout_lists)) {
            LayoutSwitch(
                title = stringResource(R.string.display_numbers),
                body = stringResource(R.string.display_numbers_body),
                checked = showNumbers,
                onChange = { settingsStore.setShowTrackNumbers(it) },
            )
            LayoutSwitch(
                title = stringResource(R.string.display_subtitle),
                body = stringResource(R.string.display_subtitle_body),
                checked = showSubtitle,
                onChange = { settingsStore.setShowTrackSubtitle(it) },
            )
            LayoutSwitch(
                title = stringResource(R.string.display_item_count),
                body = stringResource(R.string.display_item_count_body),
                checked = showItemCount,
                onChange = { settingsStore.setShowItemCount(it) },
            )
        }

        SettingsSection(title = stringResource(R.string.layout_player)) {
            Text(stringResource(R.string.np_style_label), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerStyle.entries.forEach { style ->
                    FilterChip(
                        selected = playerStyle == style,
                        onClick = { settingsStore.setPlayerStyle(style) },
                        label = {
                            Text(
                                stringResource(
                                    when (style) {
                                        PlayerStyle.STANDARD -> R.string.np_style_standard
                                        PlayerStyle.LARGE_COVER -> R.string.np_style_large
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.np_background_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerBackground.entries.forEach { background ->
                    FilterChip(
                        selected = playerBackground == background,
                        onClick = { settingsStore.setPlayerBackground(background) },
                        label = {
                            Text(
                                stringResource(
                                    when (background) {
                                        PlayerBackground.GRADIENT -> R.string.np_background_gradient
                                        PlayerBackground.SOLID -> R.string.np_background_solid
                                        PlayerBackground.ALBUM_COLOR -> R.string.np_background_album
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LayoutSwitch(
                title = stringResource(R.string.np_compact),
                body = stringResource(R.string.np_compact_body),
                checked = compactControls,
                onChange = { settingsStore.setCompactControls(it) },
            )
            LayoutSwitch(
                title = stringResource(R.string.np_fit_cover),
                body = stringResource(R.string.np_fit_cover_body),
                checked = fitCover,
                onChange = { settingsStore.setFitCover(it) },
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                settingsStore.setLibraryColumns(0)
                settingsStore.setGridSpacing(12)
                settingsStore.setLibraryDisplay(LibraryDisplay.GRID)
                settingsStore.setShowFavoriteBadge(true)
                settingsStore.setShowOfflineBadge(true)
                settingsStore.setShowTrackNumbers(true)
                settingsStore.setShowTrackSubtitle(true)
                settingsStore.setShowItemCount(false)
                settingsStore.setPlayerStyle(PlayerStyle.STANDARD)
                settingsStore.setPlayerBackground(PlayerBackground.GRADIENT)
                settingsStore.setCompactControls(false)
                settingsStore.setFitCover(false)
                settingsStore.setCoverCornerRadius(24)
                settingsStore.setCoverMarginDp(0)
                settingsStore.applyHomeSections(DefaultSections)
                Toast.makeText(context, R.string.layout_reset_done, Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.layout_reset))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Compact stand-in for the home screen so changes are visible immediately. */
@Composable
private fun LayoutPreview(
    spacing: Int,
    columns: Int,
    listMode: Boolean,
    coverRadius: Int,
    coverMargin: Int,
    compactControls: Boolean,
) {
    val tiles = if (columns in 2..5) columns else 3
    SettingsSection(topSpacing = 0.dp) {
        Text(
            stringResource(R.string.home_greeting),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Jellyfin",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .height(26.dp)
                        .width(72.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.home_recently_added),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(6.dp))
        if (listMode) {
            repeat(3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .height(9.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.45f)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)),
                        )
                    }
                }
            }
        } else {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.dp)) {
            repeat(tiles) {
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .padding(coverMargin.dp)
                        .clip(RoundedCornerShape(coverRadius.coerceAtMost(20).dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 8.dp, vertical = if (compactControls) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(if (compactControls) 28.dp else 34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.45f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)),
                )
            }
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    FilterChip(selected = false, onClick = onClick, label = { Text(label) })
}

@Composable
private fun LayoutSwitch(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val SectionOrder = listOf(
    "recent",
    "random_albums",
    "random_artists",
    "continue",
    "recent_played",
    "favorites",
    "playlists",
    "genres",
    "songs",
)

private val DefaultSections = listOf("recent", "random_albums", "random_artists")

private fun sectionLabel(key: String): Int = when (key) {
    "recent" -> R.string.home_recently_added
    "random_albums" -> R.string.home_random_albums
    "random_artists" -> R.string.home_random_artists
    "continue" -> R.string.home_continue
    "recent_played" -> R.string.home_recent_played
    "favorites" -> R.string.library_tab_favorites
    "playlists" -> R.string.library_tab_playlists
    "genres" -> R.string.library_tab_genres
    else -> R.string.home_random_songs
}
