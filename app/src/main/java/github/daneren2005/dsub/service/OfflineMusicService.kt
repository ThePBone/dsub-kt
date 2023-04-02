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
package github.daneren2005.dsub.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import github.daneren2005.dsub.Preferences
import github.daneren2005.dsub.R
import github.daneren2005.dsub.domain.Artist
import github.daneren2005.dsub.domain.ArtistInfo
import github.daneren2005.dsub.domain.ChatMessage
import github.daneren2005.dsub.domain.Genre
import github.daneren2005.dsub.domain.Indexes
import github.daneren2005.dsub.domain.InternetRadioStation
import github.daneren2005.dsub.domain.Lyrics
import github.daneren2005.dsub.domain.MusicDirectory
import github.daneren2005.dsub.domain.MusicFolder
import github.daneren2005.dsub.domain.PlayerQueue
import github.daneren2005.dsub.domain.Playlist
import github.daneren2005.dsub.domain.PodcastChannel
import github.daneren2005.dsub.domain.PodcastEpisode
import github.daneren2005.dsub.domain.RemoteStatus
import github.daneren2005.dsub.domain.SearchCritera
import github.daneren2005.dsub.domain.SearchResult
import github.daneren2005.dsub.domain.Share
import github.daneren2005.dsub.domain.User
import github.daneren2005.dsub.util.Constants
import github.daneren2005.dsub.util.FileUtil
import github.daneren2005.dsub.util.ProgressListener
import github.daneren2005.dsub.util.SilentBackgroundTask
import github.daneren2005.dsub.util.SongDBHandler
import github.daneren2005.dsub.util.Util.close
import github.daneren2005.dsub.util.Util.getOfflineSync
import github.daneren2005.dsub.util.Util.getPreferences
import github.daneren2005.dsub.util.Util.getStringDistance
import github.daneren2005.dsub.util.Util.parseOfflineIDSearch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Reader
import java.net.HttpURLConnection
import java.util.Collections
import java.util.LinkedList
import java.util.Locale
import java.util.Random

/**
 * @author Sindre Mehus
 */
class OfflineMusicService : MusicService, KoinComponent {
    private val prefs: Preferences.App by inject()

    @Throws(Exception::class)
    override fun ping(context: Context, progressListener: ProgressListener) {
    }

    @Throws(Exception::class)
    override fun isLicenseValid(context: Context, progressListener: ProgressListener): Boolean {
        return true
    }

    @Throws(Exception::class)
    override fun getIndexes(
        musicFolderId: String,
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): Indexes {
        val artists: MutableList<Artist> =
            ArrayList()
        val entries: MutableList<MusicDirectory.Entry> =
            ArrayList()
        val root = FileUtil.getMusicDirectory(context)
        for (file in FileUtil.listFiles(root)) {
            if (file.isDirectory) {
                val artist =
                    Artist()
                artist.id = file.path
                artist.index = file.name.substring(0, 1)
                artist.name = file.name
                artists.add(artist)
            } else if (file.name != "albumart.jpg" && file.name != ".nomedia") {
                entries.add(createEntry(context, file))
            }
        }
        return Indexes(0L, emptyList(), artists, entries)
    }

    @Throws(Exception::class)
    override fun getMusicDirectory(
        id: String,
        artistName: String,
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        return getMusicDirectory(id, artistName, refresh, context, progressListener, false)
    }

    @Throws(Exception::class)
    private fun getMusicDirectory(
        id: String,
        artistName: String?,
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener,
        isPodcast: Boolean
    ): MusicDirectory {
        val dir = File(id)
        val result = MusicDirectory()
        result.name = dir.name
        val names: MutableSet<String?> = HashSet()
        for (file in FileUtil.listMediaFiles(dir)) {
            val name = getName(file)
            if ((name != null) and !names.contains(name)) {
                names.add(name)
                result.addChild(createEntry(context, file, name, true, isPodcast))
            }
        }
        result.sortChildren(prefs.get(R.string.key_custom_sort_enabled))
        return result
    }

    @Throws(Exception::class)
    override fun getArtist(
        id: String,
        name: String,
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getAlbum(
        id: String,
        name: String,
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    private fun getName(file: File): String? {
        var name = file.name
        if (file.isDirectory) {
            return name
        }
        if (name.endsWith(".partial") || name.contains(".partial.") || name == Constants.ALBUM_ART_FILE) {
            return null
        }
        name = name.replace(".complete", "")
        return FileUtil.getBaseName(name)
    }

    private fun createEntry(
        context: Context,
        file: File,
        name: String? = getName(file),
        load: Boolean = true,
        isPodcast: Boolean = false
    ): MusicDirectory.Entry {
        val entry: MusicDirectory.Entry
        if (isPodcast) {
            val episode = PodcastEpisode()
            episode.status = "completed"
            entry = episode
        } else {
            entry = MusicDirectory.Entry()
        }
        entry.isDirectory = file.isDirectory
        entry.id = file.path
        entry.parent = file.parent
        entry.size = file.length()
        val root = FileUtil.getMusicDirectory(context).path
        if (file.parentFile.parentFile.path != root) {
            entry.grandParent = file.parentFile.parent
        }
        entry.path = file.path.replaceFirst("^$root/".toRegex(), "")
        var title = name
        if (file.isFile) {
            val artistFolder = file.parentFile.parentFile
            val albumFolder = file.parentFile
            if (artistFolder.path == root) {
                entry.artist = albumFolder.name
            } else {
                entry.artist = artistFolder.name
            }
            entry.album = albumFolder.name
            val index = name!!.indexOf('-')
            if (index != -1) {
                try {
                    entry.track = name.substring(0, index).toInt()
                    title = title!!.substring(index + 1)
                } catch (e: Exception) {
                    // Failed parseInt, just means track filled out
                }
            }
            if (load) {
                entry.loadMetadata(file)
            }
        }
        entry.title = title
        entry.suffix = FileUtil.getExtension(file.name.replace(".complete", ""))
        val albumArt = FileUtil.getAlbumArtFile(context, entry)
        if (albumArt.exists()) {
            entry.coverArt = albumArt.path
        }
        if (FileUtil.isVideoFile(file)) {
            entry.isVideo = true
        }
        return entry
    }

    @Throws(Exception::class)
    override fun getCoverArt(
        context: Context,
        entry: MusicDirectory.Entry,
        size: Int,
        progressListener: ProgressListener,
        task: SilentBackgroundTask<*>?
    ): Bitmap? {
        return try {
            FileUtil.getAlbumArtBitmap(context, entry, size)
        } catch (e: Exception) {
            null
        }
    }

    @Throws(Exception::class)
    override fun getDownloadInputStream(
        context: Context,
        song: MusicDirectory.Entry,
        offset: Long,
        maxBitrate: Int,
        task: SilentBackgroundTask<*>?
    ): HttpURLConnection {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getMusicUrl(
        context: Context,
        song: MusicDirectory.Entry,
        maxBitrate: Int
    ): String {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getMusicFolders(
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): List<MusicFolder> {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun startRescan(context: Context, listener: ProgressListener) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun search(
        criteria: SearchCritera,
        context: Context,
        progressListener: ProgressListener
    ): SearchResult {
        var artists: MutableList<Artist> = ArrayList()
        var albums: MutableList<MusicDirectory.Entry> = ArrayList()
        var songs: MutableList<MusicDirectory.Entry> = ArrayList()
        val root = FileUtil.getMusicDirectory(context)
        var closeness = 0
        for (artistFile in FileUtil.listFiles(root)) {
            val artistName = artistFile.name
            if (artistFile.isDirectory) {
                if (matchCriteria(criteria, artistName).also { closeness = it } > 0) {
                    val artist = Artist()
                    artist.id = artistFile.path
                    artist.index = artistFile.name.substring(0, 1)
                    artist.name = artistName
                    artist.closeness = closeness
                    artists.add(artist)
                }
                recursiveAlbumSearch(artistName, artistFile, criteria, context, albums, songs)
            }
        }
        artists.sortWith { lhs, rhs ->
            if (lhs.closeness == rhs.closeness) {
                0
            } else if (lhs.closeness > rhs.closeness) {
                -1
            } else {
                1
            }
        }
        albums.sortWith { lhs, rhs ->
            if (lhs.closeness == rhs.closeness) {
                0
            } else if (lhs.closeness > rhs.closeness) {
                -1
            } else {
                1
            }
        }
        songs.sortWith{ lhs, rhs ->
            if (lhs.closeness == rhs.closeness) {
                0
            } else if (lhs.closeness > rhs.closeness) {
                -1
            } else {
                1
            }
        }

        // Respect counts in search criteria
        val artistCount = Math.min(artists.size, criteria.artistCount)
        val albumCount = Math.min(albums.size, criteria.albumCount)
        val songCount = Math.min(songs.size, criteria.songCount)
        artists = artists.subList(0, artistCount)
        albums = albums.subList(0, albumCount)
        songs = songs.subList(0, songCount)
        return SearchResult(artists, albums, songs)
    }

    @Throws(Exception::class)
    override fun getStarredList(
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    private fun recursiveAlbumSearch(
        artistName: String,
        file: File,
        criteria: SearchCritera,
        context: Context,
        albums: MutableList<MusicDirectory.Entry>,
        songs: MutableList<MusicDirectory.Entry>
    ) {
        var closeness: Int
        for (albumFile in FileUtil.listMediaFiles(file)) {
            if (albumFile.isDirectory) {
                val albumName = getName(albumFile)
                if (matchCriteria(criteria, albumName).also { closeness = it } > 0) {
                    val album = createEntry(context, albumFile, albumName)
                    album.artist = artistName
                    album.closeness = closeness
                    albums.add(album)
                }
                for (songFile in FileUtil.listMediaFiles(albumFile)) {
                    val songName = getName(songFile) ?: continue
                    if (songFile.isDirectory) {
                        recursiveAlbumSearch(artistName, songFile, criteria, context, albums, songs)
                    } else if (matchCriteria(criteria, songName).also { closeness = it } > 0) {
                        val song = createEntry(context, albumFile, songName)
                        song.artist = artistName
                        song.album = albumName
                        song.closeness = closeness
                        songs.add(song)
                    }
                }
            } else {
                val songName = getName(albumFile)
                if (matchCriteria(criteria, songName).also { closeness = it } > 0) {
                    val song = createEntry(context, albumFile, songName)
                    song.artist = artistName
                    song.album = songName
                    song.closeness = closeness
                    songs.add(song)
                }
            }
        }
    }

    private fun matchCriteria(criteria: SearchCritera, name: String?): Int {
        return if (criteria.pattern.matcher(name).matches()) {
            getStringDistance(
                criteria.query.lowercase(Locale.getDefault()),
                name!!.lowercase(Locale.getDefault())
            )
        } else {
            0
        }
    }

    @Throws(Exception::class)
    override fun getPlaylists(
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): List<Playlist> {
        val playlists: MutableList<Playlist> = ArrayList()
        val root = FileUtil.getPlaylistDirectory(context)
        var lastServer: String? = null
        var removeServer = true
        for (folder in FileUtil.listFiles(root)) {
            if (folder.isDirectory) {
                val server = folder.name
                val fileList = FileUtil.listFiles(folder)
                for (file in fileList) {
                    if (FileUtil.isPlaylistFile(file)) {
                        val id = file.name
                        val filename = FileUtil.getBaseName(id)
                        val name = "$server: $filename"
                        val playlist = Playlist(server, name)
                        playlist.comment = filename
                        var reader: Reader? = null
                        var buffer: BufferedReader? = null
                        var songCount = 0
                        try {
                            reader = FileReader(file)
                            buffer = BufferedReader(reader)
                            var line = buffer.readLine()
                            while (buffer.readLine().also { line = it } != null) {
                                // No matter what, end file can't have .complete in it
                                line = line.replace(".complete", "")
                                val entryFile = File(line)

                                // Don't add file to playlist if it doesn't exist as cached or pinned!
                                var checkFile = entryFile
                                if (!checkFile.exists()) {
                                    // If normal file doens't exist, check if .complete version does
                                    checkFile = File(
                                        entryFile.parent, FileUtil.getBaseName(entryFile.name)
                                                + ".complete." + FileUtil.getExtension(entryFile.name)
                                    )
                                }
                                val entryName = getName(entryFile)
                                if (checkFile.exists() && entryName != null) {
                                    songCount++
                                }
                            }
                            playlist.songCount = Integer.toString(songCount)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to count songs in playlist", e)
                        } finally {
                            close(buffer)
                            close(reader)
                        }
                        if (songCount > 0) {
                            playlists.add(playlist)
                        }
                    }
                }
                if (server != lastServer && fileList.size > 0) {
                    if (lastServer != null) {
                        removeServer = false
                    }
                    lastServer = server
                }
            } else {
                // Delete legacy playlist files
                try {
                    folder.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete old playlist file: " + folder.name)
                }
            }
        }
        if (removeServer) {
            for (playlist in playlists) {
                playlist.name = playlist.name.substring(playlist.id.length + 2)
            }
        }
        return playlists
    }

    @Throws(Exception::class)
    override fun getPlaylist(
        refresh: Boolean,
        id: String,
        name: String,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        var name = name
        val downloadService = DownloadService.getInstance() ?: return MusicDirectory()
        var reader: Reader? = null
        var buffer: BufferedReader? = null
        return try {
            val firstIndex = name.indexOf(id)
            if (firstIndex != -1) {
                name = name.substring(id.length + 2)
            }
            val playlistFile = FileUtil.getPlaylistFile(context, id, name)
            reader = FileReader(playlistFile)
            buffer = BufferedReader(reader)
            val playlist = MusicDirectory()
            var line = buffer.readLine()
            if ("#EXTM3U" != line) return playlist
            while (buffer.readLine().also { line = it } != null) {
                // No matter what, end file can't have .complete in it
                line = line.replace(".complete", "")
                val entryFile = File(line)

                // Don't add file to playlist if it doesn't exist as cached or pinned!
                var checkFile = entryFile
                if (!checkFile.exists()) {
                    // If normal file doens't exist, check if .complete version does
                    checkFile = File(
                        entryFile.parent, FileUtil.getBaseName(entryFile.name)
                                + ".complete." + FileUtil.getExtension(entryFile.name)
                    )
                }
                val entryName = getName(entryFile)
                if (checkFile.exists() && entryName != null) {
                    playlist.addChild(createEntry(context, entryFile, entryName, false))
                }
            }
            playlist
        } finally {
            close(buffer)
            close(reader)
        }
    }

    @Throws(Exception::class)
    override fun createPlaylist(
        id: String,
        name: String,
        entries: List<MusicDirectory.Entry>,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun deletePlaylist(id: String, context: Context, progressListener: ProgressListener) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun addToPlaylist(
        id: String,
        toAdd: List<MusicDirectory.Entry>,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun removeFromPlaylist(
        id: String,
        toRemove: List<Int>,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun overwritePlaylist(
        id: String,
        name: String,
        toRemove: Int,
        toAdd: List<MusicDirectory.Entry>,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun updatePlaylist(
        id: String,
        name: String,
        comment: String,
        pub: Boolean,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getLyrics(
        artist: String,
        title: String,
        context: Context,
        progressListener: ProgressListener
    ): Lyrics {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun scrobble(
        id: String,
        submission: Boolean,
        context: Context,
        progressListener: ProgressListener
    ) {
        if (!submission) {
            return
        }
        val prefs = getPreferences(context)
        val cacheLocn = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null)
        val offline = getOfflineSync(context)
        var scrobbles = offline.getInt(Constants.OFFLINE_SCROBBLE_COUNT, 0)
        scrobbles++
        val offlineEditor = offline.edit()
        if (id.indexOf(cacheLocn!!) != -1) {
            val cachedSongId = SongDBHandler.getHandler(context).getIdFromPath(id)
            if (cachedSongId != null) {
                offlineEditor.putString(
                    Constants.OFFLINE_SCROBBLE_ID + scrobbles,
                    cachedSongId.second
                )
                offlineEditor.remove(Constants.OFFLINE_SCROBBLE_SEARCH + scrobbles)
            } else {
                val scrobbleSearchCriteria = parseOfflineIDSearch(context, id, cacheLocn)
                offlineEditor.putString(
                    Constants.OFFLINE_SCROBBLE_SEARCH + scrobbles,
                    scrobbleSearchCriteria
                )
                offlineEditor.remove(Constants.OFFLINE_SCROBBLE_ID + scrobbles)
            }
        } else {
            offlineEditor.putString(Constants.OFFLINE_SCROBBLE_ID + scrobbles, id)
            offlineEditor.remove(Constants.OFFLINE_SCROBBLE_SEARCH + scrobbles)
        }
        offlineEditor.putLong(
            Constants.OFFLINE_SCROBBLE_TIME + scrobbles,
            System.currentTimeMillis()
        )
        offlineEditor.putInt(Constants.OFFLINE_SCROBBLE_COUNT, scrobbles)
        offlineEditor.commit()
    }

    @Throws(Exception::class)
    override fun getAlbumList(
        type: String,
        size: Int,
        offset: Int,
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getAlbumList(
        type: String,
        extra: String,
        size: Int,
        offset: Int,
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getSongList(
        type: String,
        size: Int,
        offset: Int,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getRandomSongs(
        size: Int,
        artistId: String,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    override fun getVideoUrl(maxBitrate: Int, context: Context, id: String): String? {
        return null
    }

    @Throws(Exception::class)
    override fun getVideoStreamUrl(
        format: String,
        maxBitrate: Int,
        context: Context,
        id: String
    ): String {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getHlsUrl(id: String, bitRate: Int, context: Context): String {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun updateJukeboxPlaylist(
        ids: List<String>,
        context: Context,
        progressListener: ProgressListener
    ): RemoteStatus {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun skipJukebox(
        index: Int,
        offsetSeconds: Int,
        context: Context,
        progressListener: ProgressListener
    ): RemoteStatus {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun stopJukebox(context: Context, progressListener: ProgressListener): RemoteStatus {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun startJukebox(context: Context, progressListener: ProgressListener): RemoteStatus {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getJukeboxStatus(
        context: Context,
        progressListener: ProgressListener
    ): RemoteStatus {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun setJukeboxGain(
        gain: Float,
        context: Context,
        progressListener: ProgressListener
    ): RemoteStatus {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun setStarred(
        entries: List<MusicDirectory.Entry>,
        artists: List<MusicDirectory.Entry>,
        albums: List<MusicDirectory.Entry>,
        starred: Boolean,
        progressListener: ProgressListener,
        context: Context
    ) {
        val prefs = getPreferences(context)
        val cacheLocn = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null)
        val offline = getOfflineSync(context)
        var stars = offline.getInt(Constants.OFFLINE_STAR_COUNT, 0)
        stars++
        val offlineEditor = offline.edit()
        val id = entries[0].id
        if (id!!.indexOf(cacheLocn!!) != -1) {
            val searchCriteria = parseOfflineIDSearch(context, id, cacheLocn)
            offlineEditor.putString(Constants.OFFLINE_STAR_SEARCH + stars, searchCriteria)
            offlineEditor.remove(Constants.OFFLINE_STAR_ID + stars)
        } else {
            offlineEditor.putString(Constants.OFFLINE_STAR_ID + stars, id)
            offlineEditor.remove(Constants.OFFLINE_STAR_SEARCH + stars)
        }
        offlineEditor.putBoolean(Constants.OFFLINE_STAR_SETTING + stars, starred)
        offlineEditor.putInt(Constants.OFFLINE_STAR_COUNT, stars)
        offlineEditor.commit()
    }

    @Throws(Exception::class)
    override fun getShares(context: Context, progressListener: ProgressListener): List<Share> {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun createShare(
        ids: List<String>,
        description: String,
        expires: Long,
        context: Context,
        progressListener: ProgressListener
    ): List<Share> {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun deleteShare(id: String, context: Context, progressListener: ProgressListener) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun updateShare(
        id: String,
        description: String,
        expires: Long,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getChatMessages(
        since: Long,
        context: Context,
        progressListener: ProgressListener
    ): List<ChatMessage> {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun addChatMessage(
        message: String,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getGenres(
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): List<Genre> {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getSongsByGenre(
        genre: String,
        count: Int,
        offset: Int,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getTopTrackSongs(
        artist: String,
        size: Int,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getRandomSongs(
        size: Int,
        folder: String,
        genre: String,
        startYear: String,
        endYear: String,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        val root = FileUtil.getMusicDirectory(context)
        val children: MutableList<File> = LinkedList()
        listFilesRecursively(root, children)
        val result = MusicDirectory()
        if (children.isEmpty()) {
            return result
        }
        for (i in 0 until size) {
            val file = children[random.nextInt(children.size)]
            result.addChild(createEntry(context, file, getName(file)))
        }
        return result
    }

    @Throws(Exception::class)
    override fun getCoverArtUrl(context: Context, entry: MusicDirectory.Entry): String {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getPodcastChannels(
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): List<PodcastChannel> {
        val channels: MutableList<PodcastChannel> = ArrayList()
        val dir = FileUtil.getPodcastDirectory(context)
        var line: String
        for (file in dir.listFiles()) {
            val br = BufferedReader(FileReader(file))
            while (br.readLine().also { line = it } != null && "" != line) {
                val parts = line.split("\t".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val channel = PodcastChannel()
                channel.id = parts[0]
                channel.name = parts[0]
                channel.status = "completed"
                val albumArt = FileUtil.getAlbumArtFile(context, channel)
                if (albumArt.exists()) {
                    channel.coverArt = albumArt.path
                }
                if (parts.size > 1) {
                    channel.url = parts[1]
                }
                if (FileUtil.getPodcastDirectory(context, channel).exists() && !channels.contains(
                        channel
                    )
                ) {
                    channels.add(channel)
                }
            }
            br.close()
        }
        return channels
    }

    @Throws(Exception::class)
    override fun getPodcastEpisodes(
        refresh: Boolean,
        id: String,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        return getMusicDirectory(
            FileUtil.getPodcastDirectory(context, id).path,
            null,
            false,
            context,
            progressListener,
            true
        )
    }

    @Throws(Exception::class)
    override fun getNewestPodcastEpisodes(
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener,
        count: Int
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun refreshPodcasts(context: Context, progressListener: ProgressListener) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun createPodcastChannel(
        url: String,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun deletePodcastChannel(
        id: String,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun downloadPodcastEpisode(
        id: String,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun deletePodcastEpisode(
        id: String,
        parent: String,
        progressListener: ProgressListener,
        context: Context
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun setRating(
        entry: MusicDirectory.Entry,
        rating: Int,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getBookmarks(
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun createBookmark(
        entry: MusicDirectory.Entry,
        position: Int,
        comment: String,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun deleteBookmark(
        entry: MusicDirectory.Entry,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getUser(
        refresh: Boolean,
        username: String,
        context: Context,
        progressListener: ProgressListener
    ): User {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getUsers(
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): List<User> {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun createUser(user: User, context: Context, progressListener: ProgressListener) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun updateUser(user: User, context: Context, progressListener: ProgressListener) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun deleteUser(
        username: String,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun changeEmail(
        username: String,
        email: String,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun changePassword(
        username: String,
        password: String,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getAvatar(
        username: String,
        size: Int,
        context: Context,
        progressListener: ProgressListener,
        task: SilentBackgroundTask<*>?
    ): Bitmap {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getArtistInfo(
        id: String,
        refresh: Boolean,
        allowNetwork: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): ArtistInfo {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getBitmap(
        url: String,
        size: Int,
        context: Context,
        progressListener: ProgressListener,
        task: SilentBackgroundTask<*>?
    ): Bitmap {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getVideos(
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): MusicDirectory {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun savePlayQueue(
        songs: List<MusicDirectory.Entry>,
        currentPlaying: MusicDirectory.Entry,
        position: Int,
        context: Context,
        progressListener: ProgressListener
    ) {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getPlayQueue(context: Context, progressListener: ProgressListener): PlayerQueue {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun getInternetRadioStations(
        refresh: Boolean,
        context: Context,
        progressListener: ProgressListener
    ): List<InternetRadioStation> {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun processOfflineSyncs(context: Context, progressListener: ProgressListener): Int {
        throw OfflineException(ERRORMSG)
    }

    @Throws(Exception::class)
    override fun setInstance(instance: Int) {
        throw OfflineException(ERRORMSG)
    }

    private fun listFilesRecursively(parent: File, children: MutableList<File>) {
        for (file in FileUtil.listMediaFiles(parent)) {
            if (file.isFile) {
                children.add(file)
            } else {
                listFilesRecursively(file, children)
            }
        }
    }

    companion object {
        private val TAG = OfflineMusicService::class.java.simpleName
        private const val ERRORMSG = "Not available in offline mode"
        private val random = Random()
    }
}