package com.maloy.muzza.ui.menu


import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.maloy.innertube.YouTube
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalDownloadUtil
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.ListItemHeight
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.models.MediaMetadata
import com.maloy.muzza.playback.ExoDownloadService
import com.maloy.muzza.ui.component.BottomSheetState
import com.maloy.muzza.ui.component.DownloadListMenu
import com.maloy.muzza.ui.component.ListMenuItem
import com.maloy.muzza.ui.component.ListDialog
import com.maloy.muzza.ui.component.ListMenu
import com.maloy.muzza.ui.component.MediaMetadataListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun MediaMetadataMenu(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    bottomSheetState: BottomSheetState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id).collectAsState(initial = null)

    val artists = remember(mediaMetadata.artists) {
        mediaMetadata.artists.filter { it.id != null }
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val coroutineScope = rememberCoroutineScope()

    AddToPlaylistDialog(
        navController = navController,
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { browseId ->
                    YouTube.addToPlaylist(browseId, mediaMetadata.id)
                }
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        }
    )

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false }
        ) {
            items(artists) { artist ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            bottomSheetState.collapseSoft()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }


    MediaMetadataListItem(
        mediaMetadata = mediaMetadata,
        badges = {},
        trailingContent = {
            val song by database.song(mediaMetadata.id).collectAsState(initial = null)

            IconButton(
                onClick = {
                    database.query {
                        val currentSong = song
                        if (currentSong == null) {
                            insert(mediaMetadata, SongEntity::toggleLike)
                        } else {
                            update(currentSong.song.toggleLike())
                        }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(if (song?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (song?.song?.liked == true) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null
                )
            }
        }
    )

    HorizontalDivider()

    ListMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        ListMenuItem(
            icon = R.drawable.radio,
            title = R.string.start_radio
        ) {
            playerConnection.service.startRadioSeamlessly()
            onDismiss()
        }
        ListMenuItem(
            icon = R.drawable.playlist_play,
            title = R.string.play_next
        ) {
            onDismiss()
            playerConnection.playNext(mediaMetadata.toMediaItem())
        }
        ListMenuItem(
            icon = R.drawable.queue_music,
            title = R.string.add_to_queue
        ) {
            onDismiss()
            playerConnection.addToQueue(mediaMetadata.toMediaItem())
        }
        ListMenuItem(
            icon = R.drawable.playlist_add,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
        DownloadListMenu(
            state = download?.state,
            onDownload = {
                database.transaction {
                    insert(mediaMetadata)
                }
                val downloadRequest = DownloadRequest.Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                    .setCustomCacheKey(mediaMetadata.id)
                    .setData(mediaMetadata.title.toByteArray())
                    .build()
                DownloadService.sendAddDownload(
                    context,
                    ExoDownloadService::class.java,
                    downloadRequest,
                    false
                )
            },
            onRemoveDownload = {
                DownloadService.sendRemoveDownload(
                    context,
                    ExoDownloadService::class.java,
                    mediaMetadata.id,
                    false
                )
            }
        )
        if (librarySong?.song?.inLibrary != null) {
            ListMenuItem(
                icon = R.drawable.library_add_check,
                title = R.string.remove_from_library,
            ) {
                database.query {
                    inLibrary(mediaMetadata.id, null)
                }
            }
        } else {
            ListMenuItem(
                icon = R.drawable.library_add,
                title = R.string.add_to_library,
            ) {
                database.transaction {
                    insert(mediaMetadata)
                    inLibrary(mediaMetadata.id, LocalDateTime.now())
                }
            }
        }
        if (artists.isNotEmpty()) {
            ListMenuItem(
                icon = R.drawable.artist,
                title = R.string.view_artist
            ) {
                if (artists.size == 1) {
                    navController.navigate("artist/${artists[0].id}")
                    bottomSheetState.collapseSoft()
                    onDismiss()
                } else {
                    showSelectArtistDialog = true
                }
            }
        }
        if (mediaMetadata.album != null) {
            ListMenuItem(
                icon = R.drawable.album,
                title = R.string.view_album
            ) {
                navController.navigate("album/${mediaMetadata.album.id}")
                bottomSheetState.collapseSoft()
                onDismiss()
            }
        }
        ListMenuItem(
            icon = R.drawable.share,
            title = R.string.share
        ) {
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${mediaMetadata.id}")
            }
            context.startActivity(Intent.createChooser(intent, null))
            onDismiss()
        }
    }
}