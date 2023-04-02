/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package github.daneren2005.dsub.view

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import github.daneren2005.dsub.Preferences
import github.daneren2005.dsub.R
import github.daneren2005.dsub.domain.MusicDirectory
import github.daneren2005.dsub.domain.PodcastEpisode
import github.daneren2005.dsub.service.DownloadFile
import github.daneren2005.dsub.service.DownloadService
import github.daneren2005.dsub.util.DrawableTint
import github.daneren2005.dsub.util.SongDBHandler
import github.daneren2005.dsub.util.ThemeUtil.getTheme
import github.daneren2005.dsub.util.Util.equals
import github.daneren2005.dsub.util.Util.formatDate
import github.daneren2005.dsub.util.Util.formatDuration
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Used to display songs in a `ListView`.
 *
 * @author Sindre Mehus
 */
class SongView(context: Context?) : UpdateView2<MusicDirectory.Entry?, Boolean?>(context), KoinComponent {
    private val trackTextView: TextView
    private val titleTextView: TextView
    private var playingTextView: TextView? = null
    private val artistTextView: TextView
    private val durationTextView: TextView
    private val statusTextView: TextView
    private val statusImageView: ImageView
    private val bookmarkButton: ImageView
    private val playedButton: ImageView
    private val bottomRowView: View
    private val suffixTextView: TextView
    private var downloadService: DownloadService? = null
    private var revision: Long = -1
    private var downloadFile: DownloadFile? = null
    private var dontChangeDownloadFile = false
    private var playing = false
    private var rightImage = false
    private var moreImage = 0
    private var isWorkDone = false
    private var isSaved = false
    private var partialFile: File? = null
    private var partialFileExists = false
    private var loaded = false
    private var isBookmarked = false
    private var isBookmarkedShown = false
    private var showPodcast = false
    private var isPlayed = false
    private var isPlayedShown = false
    private var showAlbum = false

    private val prefs: Preferences.App by inject()


    init {
        LayoutInflater.from(context).inflate(R.layout.song_list_item, this, true)
        trackTextView = findViewById<View>(R.id.song_track) as TextView
        titleTextView = findViewById<View>(R.id.song_title) as TextView
        artistTextView = findViewById<View>(R.id.song_artist) as TextView
        durationTextView = findViewById<View>(R.id.song_duration) as TextView
        statusTextView = findViewById<View>(R.id.song_status) as TextView
        statusImageView = findViewById<View>(R.id.song_status_icon) as ImageView
        ratingBar = findViewById<View>(R.id.song_rating) as RatingBar
        starButton = findViewById<View>(R.id.song_star) as ImageButton
        starButton.isFocusable = false
        bookmarkButton = findViewById<View>(R.id.song_bookmark) as ImageButton
        bookmarkButton.isFocusable = false
        playedButton = findViewById<View>(R.id.song_played) as ImageButton
        moreButton = findViewById<View>(R.id.item_more) as ImageView
        bottomRowView = findViewById(R.id.song_bottom)
        suffixTextView = findViewById<View>(R.id.song_suffix) as TextView
    }

    override fun setObjectImpl(song: MusicDirectory.Entry?, checkable: Boolean?) {
        this.checkable = checkable ?: false
        val artist = StringBuilder(40)
        song ?: return
        val isPodcast = song is PodcastEpisode
        if (!song.isVideo || isPodcast) {
            if (isPodcast) {
                val episode = song as PodcastEpisode
                if (showPodcast && episode.artist != null) {
                    artist.append(episode.artist)
                }
                val date = episode.date
                if (date != null) {
                    if (artist.length != 0) {
                        artist.append(" - ")
                    }
                    artist.append(formatDate(context, date, false))
                }
            } else if (song.artist != null) {
                if (showAlbum) {
                    artist.append(song.album)
                } else {
                    artist.append(song.artist)
                }
            }
            if (isPodcast) {
                val status = (song as PodcastEpisode).status
                var statusRes = -1
                if ("error" == status) {
                    statusRes = R.string.song_details_error
                } else if ("skipped" == status) {
                    statusRes = R.string.song_details_skipped
                } else if ("downloading" == status) {
                    statusRes = R.string.song_details_downloading
                }
                if (statusRes != -1) {
                    artist.append(" (")
                    artist.append(getContext().getString(statusRes))
                    artist.append(")")
                }
            }
            durationTextView.text = formatDuration(song.duration)
            bottomRowView.visibility = VISIBLE
        } else {
            bottomRowView.visibility = GONE
            statusTextView.text = formatDuration(song.duration)
        }
        val title = song.title
        var track = song.track
        if (song.customOrder != null) {
            track = song.customOrder
        }
        val newPlayingTextView: TextView
        if (track != null && prefs.get(R.string.key_display_track)) {
            trackTextView.text = String.format("%02d", track)
            trackTextView.visibility = VISIBLE
            newPlayingTextView = trackTextView
        } else {
            trackTextView.visibility = GONE
            newPlayingTextView = titleTextView
        }
        if (newPlayingTextView !== playingTextView || playingTextView == null) {
            if (playing) {
                playingTextView!!.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                playing = false
            }
            playingTextView = newPlayingTextView
        }
        titleTextView.text = title
        artistTextView.text = artist
        if (prefs.get(R.string.key_display_file_suffix)) {
            suffixTextView.text = song.suffix
        }
        setBackgroundColor(0x00000000)
        ratingBar.visibility = GONE
        rating = 0
        revision = -1
        loaded = false
        dontChangeDownloadFile = false
    }

    fun setDownloadFile(downloadFile: DownloadFile?) {
        this.downloadFile = downloadFile
        dontChangeDownloadFile = true
    }

    fun getDownloadFile(): DownloadFile? {
        return downloadFile
    }

    override fun updateBackground() {
        if (downloadService == null) {
            downloadService = DownloadService.getInstance()
            if (downloadService == null) {
                return
            }
        }
        val newRevision = downloadService!!.downloadListUpdateRevision
        if (revision != newRevision && !dontChangeDownloadFile || downloadFile == null) {
            downloadFile = downloadService!!.forSong(item)
            revision = newRevision
        }
        isWorkDone = downloadFile!!.isWorkDone
        isSaved = downloadFile!!.isSaved
        partialFile = downloadFile!!.partialFile
        partialFileExists = partialFile!!.exists()
        isStarred = item!!.isStarred()
        isBookmarked = item!!.bookmark != null
        isRated = item!!.getRating()

        // Check if needs to load metadata: check against all fields that we know are null in offline mode
        if (item!!.bitRate == null && item!!.duration == null && item!!.discNumber == null && isWorkDone) {
            item!!.loadMetadata(downloadFile!!.completeFile)
            loaded = true
        }
        if (item is PodcastEpisode || item!!.isAudioBook || item!!.isPodcast) {
            isPlayed = SongDBHandler.getHandler(context).hasBeenCompleted(item)
        }
    }

    override fun update() {
        if (loaded) {
            setObjectImpl(item, item2)
        }
        if (downloadService == null || downloadFile == null) {
            return
        }
        if (item!!.isStarred()) {
            if (!starred) {
                if (starButton.drawable == null) {
                    starButton.setImageDrawable(
                        DrawableTint.getTintedDrawable(
                            context,
                            R.drawable.ic_toggle_star
                        )
                    )
                }
                starButton.visibility = VISIBLE
                starred = true
            }
        } else {
            if (starred) {
                starButton.visibility = GONE
                starred = false
            }
        }
        if (isWorkDone) {
            val moreImage = if (isSaved) R.drawable.download_pinned else R.drawable.download_cached
            if (moreImage != this.moreImage) {
                moreButton.setImageResource(moreImage)
                this.moreImage = moreImage
            }
        } else if (moreImage != R.drawable.download_none_light) {
            moreButton.setImageResource(DrawableTint.getDrawableRes(context, R.attr.download_none))
            moreImage = R.drawable.download_none_light
        }
        if (downloadFile!!.isDownloading && !downloadFile!!.isDownloadCancelled && partialFileExists) {
            var percentage = partialFile!!.length() * 100.0 / downloadFile!!.estimatedSize
            percentage = Math.min(percentage, 100.0)
            statusTextView.text = percentage.toInt().toString() + " %"
            if (!rightImage) {
                statusImageView.visibility = VISIBLE
                rightImage = true
            }
        } else if (rightImage) {
            statusTextView.text = null
            statusImageView.visibility = GONE
            rightImage = false
        }
        val playing = equals(downloadService!!.currentPlaying, downloadFile)
        if (playing) {
            if (!this.playing) {
                this.playing = playing
                playingTextView!!.setCompoundDrawablesWithIntrinsicBounds(
                    DrawableTint.getDrawableRes(
                        context,
                        R.attr.playing
                    ), 0, 0, 0
                )
            }
        } else {
            if (this.playing) {
                this.playing = playing
                playingTextView!!.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
        if (isBookmarked) {
            if (!isBookmarkedShown) {
                if (bookmarkButton.drawable == null) {
                    bookmarkButton.setImageDrawable(
                        DrawableTint.getTintedDrawable(
                            context,
                            R.drawable.ic_menu_bookmark_selected
                        )
                    )
                }
                bookmarkButton.visibility = VISIBLE
                isBookmarkedShown = true
            }
        } else {
            if (isBookmarkedShown) {
                bookmarkButton.visibility = GONE
                isBookmarkedShown = false
            }
        }
        if (isPlayed) {
            if (!isPlayedShown) {
                if (playedButton.drawable == null) {
                    playedButton.setImageDrawable(
                        DrawableTint.getTintedDrawable(
                            context,
                            R.drawable.ic_toggle_played
                        )
                    )
                }
                playedButton.visibility = VISIBLE
                isPlayedShown = true
            }
        } else {
            if (isPlayedShown) {
                playedButton.visibility = GONE
                isPlayedShown = false
            }
        }
        if (isRated != rating) {
            if (isRated > 1) {
                if (rating <= 1) {
                    ratingBar.visibility = VISIBLE
                }
                ratingBar.numStars = isRated
                ratingBar.rating = isRated.toFloat()
            } else if (isRated <= 1) {
                if (rating > 1) {
                    ratingBar.visibility = GONE
                }
            }

            // Still highlight red if a 1-star
            if (isRated == 1) {
                setBackgroundColor(Color.RED)
                val theme = getTheme(context)
                if ("black" == theme) {
                    this.background.alpha = 80
                } else if ("dark" == theme || "holo" == theme) {
                    this.background.alpha = 60
                } else {
                    this.background.alpha = 20
                }
            } else if (rating == 1) {
                setBackgroundColor(0x00000000)
            }
            rating = isRated
        }
    }

    val entry: MusicDirectory.Entry
        get() = item!!

    fun setShowPodcast(showPodcast: Boolean) {
        this.showPodcast = showPodcast
    }

    fun setShowAlbum(showAlbum: Boolean) {
        this.showAlbum = showAlbum
    }

    companion object {
        private val TAG = SongView::class.java.simpleName
    }
}