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
package github.daneren2005.dsub.domain

import android.annotation.TargetApi
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import github.daneren2005.dsub.Preferences
import github.daneren2005.dsub.R
import github.daneren2005.dsub.service.DownloadService
import github.daneren2005.dsub.util.Constants
import github.daneren2005.dsub.util.UpdateHelper.EntryInstanceUpdater
import github.daneren2005.dsub.util.Util.equals
import github.daneren2005.dsub.util.Util.getPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectInputStream
import java.io.ObjectOutput
import java.io.ObjectOutputStream
import java.io.Serializable
import java.text.Collator
import java.util.Collections
import java.util.Locale

/**
 * @author Sindre Mehus
 */
class MusicDirectory : Serializable, KoinComponent {
    private val prefs: Preferences.App by inject()

    @JvmField
	var name: String? = null
    @JvmField
	var id: String? = null
    @JvmField
	var parent: String? = null
    private var children: MutableList<Entry>

    constructor() {
        children = ArrayList()
    }

    constructor(children: MutableList<Entry>) {
        this.children = children
    }

    fun addChild(child: Entry?) {
        if (child != null) {
            children.add(child)
        }
    }

    fun addChildren(children: List<Entry>) {
        this.children.addAll(children)
    }

    fun replaceChildren(children: MutableList<Entry>) {
        this.children = children
    }

    @Synchronized
    fun getChildren(): List<Entry?> {
        return getChildren(true, true)
    }

    @Synchronized
    fun getChildren(includeDirs: Boolean, includeFiles: Boolean): List<Entry?> {
        if (includeDirs && includeFiles) {
            return children
        }
        val result: MutableList<Entry?> = ArrayList(children.size)
        for (child in children) {
            if (child != null && child.isDirectory && includeDirs || !child!!.isDirectory && includeFiles) {
                result.add(child)
            }
        }
        return result
    }

    @get:Synchronized
    val songs: List<Entry>
        get() {
            val result: MutableList<Entry> = ArrayList()
            for (child in children) {
                if (child != null && !child.isDirectory && !child.isVideo) {
                    result.add(child)
                }
            }
            return result
        }

    @get:Synchronized
    val childrenSize: Int
        get() = children.size

    fun shuffleChildren() {
        Collections.shuffle(children)
    }

    fun sortChildren(context: Context?, instance: Int) {
        // Only apply sorting on server version 4.7 and greater, where disc is supported
        if (ServerInfo.checkServerVersion(context, "1.8", instance)) {
            sortChildren(
                prefs.get(R.string.key_custom_sort_enabled)
            )
        }
    }

    fun sortChildren(byYear: Boolean) {
        EntryComparator.sort(children, byYear)
    }

    @Synchronized
    fun updateMetadata(refreshedDirectory: MusicDirectory): Boolean {
        var metadataUpdated = false
        val it: Iterator<Entry?> = children.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val index = refreshedDirectory.children.indexOf(entry)
            if (index != -1) {
                val refreshed = refreshedDirectory.children[index]
                entry!!.title = refreshed!!.title
                entry.album = refreshed.album
                entry.artist = refreshed.artist
                entry.track = refreshed.track
                entry.year = refreshed.year
                entry.genre = refreshed.genre
                entry.transcodedContentType = refreshed.transcodedContentType
                entry.transcodedSuffix = refreshed.transcodedSuffix
                entry.discNumber = refreshed.discNumber
                entry.setStarred(refreshed.isStarred())
                entry.setRating(refreshed.getRating())
                entry.type = refreshed.type
                if (!equals(entry.coverArt, refreshed.coverArt)) {
                    metadataUpdated = true
                    entry.coverArt = refreshed.coverArt
                }
                object : EntryInstanceUpdater(entry) {
                    override fun update(found: Entry) {
                        found.title = refreshed.title
                        found.album = refreshed.album
                        found.artist = refreshed.artist
                        found.track = refreshed.track
                        found.year = refreshed.year
                        found.genre = refreshed.genre
                        found.transcodedContentType = refreshed.transcodedContentType
                        found.transcodedSuffix = refreshed.transcodedSuffix
                        found.discNumber = refreshed.discNumber
                        found.setStarred(refreshed.isStarred())
                        found.setRating(refreshed.getRating())
                        found.type = refreshed.type
                        if (!equals(found.coverArt, refreshed.coverArt)) {
                            found.coverArt = refreshed.coverArt
                            metadataUpdate = DownloadService.METADATA_UPDATED_COVER_ART
                        }
                    }
                }.execute()
            }
        }
        return metadataUpdated
    }

    @Synchronized
    fun updateEntriesList(
        context: Context?,
        instance: Int,
        refreshedDirectory: MusicDirectory
    ): Boolean {
        var changed = false
        val it = children.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            // No longer exists in here
            if (refreshedDirectory.children.indexOf(entry) == -1) {
                it.remove()
                changed = true
            }
        }

        // Make sure we contain all children from refreshed set
        var resort = false
        for (refreshed in refreshedDirectory.children) {
            if (!children.contains(refreshed)) {
                children.add(refreshed)
                resort = true
                changed = true
            }
        }
        if (resort) {
            this.sortChildren(context, instance)
        }
        return changed
    }

    open class Entry : Serializable {
        @JvmField
		var id: String? = null
        @JvmField
		var parent: String? = null
        @JvmField
		var grandParent: String? = null
        @JvmField
		var albumId: String? = null
        @JvmField
		var artistId: String? = null
        var isDirectory = false
        @JvmField
		var title: String? = null
        @JvmField
		var album: String? = null
        @JvmField
		var artist: String? = null
        @JvmField
		var track: Int? = null
        @JvmField
		var customOrder: Int? = null
        @JvmField
		var year: Int? = null
        @JvmField
		var genre: String? = null
        @JvmField
		var contentType: String? = null
        @JvmField
		var suffix: String? = null
        @JvmField
		var transcodedContentType: String? = null
        @JvmField
		var transcodedSuffix: String? = null
        @JvmField
		var coverArt: String? = null
        @JvmField
		var size: Long? = null
        @JvmField
		var duration: Int? = null
        @JvmField
		var bitRate: Int? = null
        @JvmField
		var path: String? = null
        var isVideo = false
        @JvmField
		var discNumber: Int? = null
        private var starred = false
        private var rating: Int? = null
        @JvmField
		var bookmark: Bookmark? = null
        @JvmField
		var type = 0
        @JvmField
		var closeness = 0

        @Transient
        private var linkedArtist: Artist? = null

        constructor()
        constructor(id: String?) {
            this.id = id
        }

        constructor(artist: Artist) {
            id = artist.id
            title = artist.name
            isDirectory = true
            starred = artist.isStarred
            rating = artist.rating
            linkedArtist = artist
        }

        @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
        fun loadMetadata(file: File) {
            try {
                val metadata = MediaMetadataRetriever()
                metadata.setDataSource(file.absolutePath)
                var discNumber =
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                if (discNumber == null) {
                    discNumber = "1/1"
                }
                val slashIndex = discNumber.indexOf("/")
                if (slashIndex > 0) {
                    discNumber = discNumber.substring(0, slashIndex)
                }
                try {
                    this.discNumber = discNumber.toInt()
                } catch (e: Exception) {
                    Log.w(TAG, "Non numbers in disc field!")
                }
                val bitrate = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                bitRate = (bitrate ?: "0").toInt() / 1000
                val length = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = length!!.toInt() / 1000
                val artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                if (artist != null) {
                    this.artist = artist
                }
                val album = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                if (album != null) {
                    this.album = album
                }
                metadata.release()
            } catch (e: Exception) {
                Log.i(TAG, "Device doesn't properly support MediaMetadataRetreiver", e)
            }
        }

        fun rebaseTitleOffPath() {
            try {
                var filename = path ?: return
                var index = filename.lastIndexOf('/')
                if (index != -1) {
                    filename = filename.substring(index + 1)
                    if (track != null) {
                        filename = filename.replace(String.format("%02d ", track), "")
                    }
                    index = filename.lastIndexOf('.')
                    if (index != -1) {
                        filename = filename.substring(0, index)
                    }
                    title = filename
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update title based off of path", e)
            }
        }

        fun isAlbum(): Boolean {
            return parent != null || artist != null
        }

        val albumDisplay: String?
            get() = if (album != null && title!!.startsWith("Disc ")) {
                album
            } else {
                title
            }

        fun isStarred(): Boolean {
            return starred
        }

        fun setStarred(starred: Boolean) {
            this.starred = starred
            if (linkedArtist != null) {
                linkedArtist!!.isStarred = starred
            }
        }

        fun getRating(): Int {
            return if (rating == null) 0 else rating!!
        }

        fun setRating(rating: Int?) {
            if (rating == null || rating == 0) {
                this.rating = null
            } else {
                this.rating = rating
            }
            if (linkedArtist != null) {
                linkedArtist!!.setRating(rating)
            }
        }

        val isSong: Boolean
            get() = type == TYPE_SONG
        val isPodcast: Boolean
            get() = this is PodcastEpisode || type == TYPE_PODCAST
        val isAudioBook: Boolean
            get() = type == TYPE_AUDIO_BOOK

        fun isOnlineId(context: Context?): Boolean {
            return try {
                val cacheLocation = getPreferences(
                    context!!
                ).getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null)
                cacheLocation == null || id == null || id!!.indexOf(cacheLocation) == -1
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check online id validity")

                // Err on the side of default functionality
                true
            }
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val entry = o as Entry
            return id == entry.id
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return title!!
        }

        @Throws(IOException::class)
        fun toByteArray(): ByteArray {
            val bos = ByteArrayOutputStream()
            var out: ObjectOutput? = null
            return try {
                out = ObjectOutputStream(bos)
                out.writeObject(this)
                out.flush()
                bos.toByteArray()
            } finally {
                try {
                    bos.close()
                } catch (ex: IOException) {
                    // ignore close exception
                }
            }
        }

        companion object {
            const val TYPE_SONG = 0
            const val TYPE_PODCAST = 1
            const val TYPE_AUDIO_BOOK = 2
            @JvmStatic
			@Throws(IOException::class, ClassNotFoundException::class)
            fun fromByteArray(byteArray: ByteArray?): Entry {
                val bis = ByteArrayInputStream(byteArray)
                var `in`: ObjectInput? = null
                return try {
                    `in` = ObjectInputStream(bis)
                    `in`.readObject() as Entry
                } finally {
                    try {
                        `in`?.close()
                    } catch (ex: IOException) {
                        // ignore close exception
                    }
                }
            }
        }
    }

    class EntryComparator(private val byYear: Boolean) : Comparator<Entry> {
        private val collator: Collator = Collator.getInstance(Locale.US)

        init {
            collator.strength = Collator.PRIMARY
        }

        override fun compare(lhs: Entry, rhs: Entry): Int {
            if (lhs.isDirectory && !rhs.isDirectory) {
                return -1
            } else if (!lhs.isDirectory && rhs.isDirectory) {
                return 1
            } else if (lhs.isDirectory && rhs.isDirectory) {
                if (byYear) {
                    val lhsYear = lhs.year
                    val rhsYear = rhs.year
                    if (lhsYear != null && rhsYear != null) {
                        return lhsYear.compareTo(rhsYear)
                    } else if (lhsYear != null) {
                        return -1
                    } else if (rhsYear != null) {
                        return 1
                    }
                }
                return collator.compare(lhs.albumDisplay, rhs.albumDisplay)
            }
            val lhsDisc = lhs.discNumber
            val rhsDisc = rhs.discNumber
            if (lhsDisc != null && rhsDisc != null) {
                if (lhsDisc < rhsDisc) {
                    return -1
                } else if (lhsDisc > rhsDisc) {
                    return 1
                }
            }
            val lhsTrack = lhs.track
            val rhsTrack = rhs.track
            if (lhsTrack === rhsTrack) {
                return collator.compare(lhs.title, rhs.title)
            } else if (lhsTrack != null && rhsTrack != null) {
                return lhsTrack.compareTo(rhsTrack)
            } else if (lhsTrack != null) {
                return -1
            }
            return 1
        }

        companion object {
            @JvmOverloads
            fun sort(entries: List<Entry>, byYear: Boolean = true) {
                try {
                    entries.sortedWith(EntryComparator(byYear))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sort MusicDirectory")
                }
            }
        }
    }

    companion object {
        private val TAG = MusicDirectory::class.java.simpleName
    }
}