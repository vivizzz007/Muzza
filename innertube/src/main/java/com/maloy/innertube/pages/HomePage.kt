package com.maloy.innertube.pages

import com.maloy.innertube.models.Album
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.Artist
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.BrowseEndpoint
import com.maloy.innertube.models.MusicCarouselShelfRenderer
import com.maloy.innertube.models.MusicResponsiveListItemRenderer
import com.maloy.innertube.models.MusicTwoRowItemRenderer
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SectionListRenderer
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.YTItem
import com.maloy.innertube.models.filterExplicit
import com.maloy.innertube.models.oddElements

data class HomePage(
    val chips: List<Chip?>?,
    val sections: List<Section>,
    val continuation: String? = null,
) {
    data class Chip(
        val title: String,
        val endpoint: BrowseEndpoint?,
        val deselectEndPoint: BrowseEndpoint?,
    ) {
        companion object {
            fun fromChipCloudChipRenderer(renderer: SectionListRenderer.Header.ChipCloudRenderer.Chip): Chip? {
                return Chip(
                    title = renderer.chipCloudChipRenderer.text?.runs?.firstOrNull()?.text
                        ?: return null,
                    endpoint = renderer.chipCloudChipRenderer.navigationEndpoint.browseEndpoint,
                    deselectEndPoint = renderer.chipCloudChipRenderer.onDeselectedCommand?.browseEndpoint,
                )
            }
        }
    }
    data class Section(
        val title: String,
        val label: String?,
        val thumbnail: String?,
        val endpoint: BrowseEndpoint?,
        val items: List<YTItem>,
        val sectionType: SectionType,
    ) {
        companion object {
            fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): Section? {
                return Section(
                    title = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                        ?: return null,
                    label = renderer.header.musicCarouselShelfBasicHeaderRenderer.strapline?.runs?.firstOrNull()?.text,
                    thumbnail = renderer.header.musicCarouselShelfBasicHeaderRenderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),
                    endpoint = renderer.header.musicCarouselShelfBasicHeaderRenderer.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint,
                    items = renderer.contents.mapNotNull {
                        it.musicTwoRowItemRenderer?.let { renderer ->
                            fromMusicTwoRowItemRenderer(
                                renderer
                            )
                        } ?: it.musicResponsiveListItemRenderer?.let { renderer ->
                            fromMusicResponsiveListItemRenderer(
                                renderer
                            )
                        }
                    }.ifEmpty {
                        return null
                    },
                    sectionType = if (renderer.contents.any { it.musicResponsiveListItemRenderer != null }) SectionType.GRID else SectionType.LIST,
                )
            }

            private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
                return when {
                    renderer.isSong -> {
                        val subtitleRuns = renderer.subtitle?.runs ?: return null
                        val (artistRuns, albumRuns) = subtitleRuns.partition { run ->
                            run.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("UC") == true
                        }
                        val artists = artistRuns.map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return null
                            )
                        }
                        SongItem(
                            id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            artists = artists,
                            album = albumRuns.firstOrNull { run ->
                                run.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("MPREb_") == true
                            }?.let { run ->
                                val endpoint = run.navigationEndpoint?.browseEndpoint ?: return null
                                Album(
                                    name = run.text,
                                    id = endpoint.browseId
                                )
                            },
                            duration = null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                                ?: return null,
                            explicit = renderer.subtitleBadges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true
                        )
                    }
                    renderer.isAlbum -> {
                        AlbumItem(
                            browseId = renderer.navigationEndpoint.browseEndpoint?.browseId
                                ?: return null,
                            playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                                ?.musicPlayButtonRenderer?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint?.playlistId ?: return null,
                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            artists = renderer.subtitle?.runs?.oddElements()?.drop(1)?.map {
                                Artist(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId
                                )
                            },
                            year = null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                                ?: return null,
                            explicit = renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null
                        )
                    }

                    renderer.isPlaylist -> {
                        PlaylistItem(
                            id = renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix(
                                "VL"
                            ) ?: return null,
                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            author = Artist(
                                name = renderer.subtitle?.runs?.lastOrNull()?.text ?: return null,
                                id = null
                            ),
                            songCountText = null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                                ?: return null,
                            playEndpoint = renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer?.content
                                ?.musicPlayButtonRenderer?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                            shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                                ?: return null,
                            radioEndpoint = renderer.menu.menuRenderer.items.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                        )
                    }

                    renderer.isArtist -> {
                        ArtistItem(
                            id = renderer.navigationEndpoint.browseEndpoint?.browseId
                                ?: return null,
                            title = renderer.title.runs?.lastOrNull()?.text ?: return null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                                ?: return null,
                            shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                                ?: return null,
                            radioEndpoint = renderer.menu.menuRenderer.items.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                                ?: return null,
                        )
                    }

                    else -> null
                }
            }
            private fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? {
                return when {
                    renderer.isSong -> {
                        SongItem(
                            id = renderer.playlistItemData?.videoId ?: return null,
                            title = renderer.flexColumns.firstOrNull()
                                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                                ?: return null,
                            artists = renderer.flexColumns.getOrNull(1)
                                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                                ?.mapNotNull { it ->
                                    it.takeIf { run ->
                                        run.navigationEndpoint?.browseEndpoint?.browseId != null
                                    }?.let {
                                        Artist(
                                            name = it.text,
                                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                                        )
                                    }
                                } ?: emptyList(),
                            album = renderer.flexColumns.getOrNull(2)
                                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                                ?.takeIf {
                                    it.navigationEndpoint?.browseEndpoint?.browseId != null
                                }?.let {
                                    it.navigationEndpoint?.browseEndpoint?.browseId?.let { it1 ->
                                        Album(
                                            name = it.text,
                                            id = it1
                                        )
                                    }
                                },
                            duration = null,
                            thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                                ?: return null,
                            explicit = renderer.badges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true
                        )
                    }

                    else -> null
                }
            }
        }
    }

    enum class SectionType {
        LIST, GRID
    }

    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) {
            copy(sections = sections.map {
                it.copy(items = it.items.filterExplicit())
            })
        } else this

}
