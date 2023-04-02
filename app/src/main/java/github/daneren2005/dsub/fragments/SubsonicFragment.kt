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
package github.daneren2005.dsub.fragments

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.util.Log
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import github.daneren2005.dsub.R
import github.daneren2005.dsub.activity.SubsonicFragmentActivity
import github.daneren2005.dsub.activity.SubsonicActivity
import github.daneren2005.dsub.adapter.SectionAdapter
import github.daneren2005.dsub.domain.Artist
import github.daneren2005.dsub.domain.Genre
import github.daneren2005.dsub.domain.MusicDirectory
import github.daneren2005.dsub.domain.Playlist
import github.daneren2005.dsub.domain.PodcastEpisode
import github.daneren2005.dsub.domain.ServerInfo
import github.daneren2005.dsub.domain.Share
import github.daneren2005.dsub.service.DownloadFile
import github.daneren2005.dsub.service.DownloadService
import github.daneren2005.dsub.service.MediaStoreService
import github.daneren2005.dsub.service.MusicService
import github.daneren2005.dsub.service.MusicServiceFactory
import github.daneren2005.dsub.service.OfflineException
import github.daneren2005.dsub.service.ServerTooOldException
import github.daneren2005.dsub.util.Constants
import github.daneren2005.dsub.util.FileUtil
import github.daneren2005.dsub.util.ImageLoader
import github.daneren2005.dsub.util.LoadingTask
import github.daneren2005.dsub.util.MenuUtil
import github.daneren2005.dsub.util.ProgressListener
import github.daneren2005.dsub.util.SilentBackgroundTask
import github.daneren2005.dsub.util.SongDBHandler
import github.daneren2005.dsub.util.UpdateHelper
import github.daneren2005.dsub.util.UpdateHelper.EntryInstanceUpdater
import github.daneren2005.dsub.util.UserUtil
import github.daneren2005.dsub.util.Util.confirmDialog
import github.daneren2005.dsub.util.Util.formatBoolean
import github.daneren2005.dsub.util.Util.formatBytes
import github.daneren2005.dsub.util.Util.formatDate
import github.daneren2005.dsub.util.Util.formatDuration
import github.daneren2005.dsub.util.Util.formatLocalizedBytes
import github.daneren2005.dsub.util.Util.getMaxVideoBitrate
import github.daneren2005.dsub.util.Util.getPreferences
import github.daneren2005.dsub.util.Util.getSongPressAction
import github.daneren2005.dsub.util.Util.getVideoPlayerType
import github.daneren2005.dsub.util.Util.isExternalStoragePresent
import github.daneren2005.dsub.util.Util.isOffline
import github.daneren2005.dsub.util.Util.isTagBrowsing
import github.daneren2005.dsub.util.Util.showDetailsDialog
import github.daneren2005.dsub.util.Util.startActivityWithoutTransition
import github.daneren2005.dsub.util.Util.toast
import github.daneren2005.dsub.view.GridSpacingDecoration
import github.daneren2005.dsub.view.PlaylistSongView
import github.daneren2005.dsub.view.UpdateView
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.LinkedList
import java.util.Random

open class SubsonicFragment : Fragment(), OnRefreshListener {
    val act
        get() = requireActivity() as SubsonicActivity

    var supportTag: Int
    @JvmField
	protected var context: SubsonicActivity? = null
    @JvmField
	protected var title: CharSequence? = null
    @JvmField
	protected var subtitle: CharSequence? = null
    @JvmField
	protected var rootView: View? = null
    @JvmField
	protected var primaryFragment = false
    @JvmField
	protected var secondaryFragment = false
    @JvmField
	protected var isOnlyVisible = true
    @JvmField
    var isAlwaysFullscreen = false

    @JvmField
    var isAlwaysStartFullscreen = false

    @JvmField
	protected var invalidated = false
    @JvmField
    var gestureDetector: GestureDetector? = null
    @JvmField
	protected var share: Share? = null
    @JvmField
	protected var artist = false
    protected var artistOverride = false
    @JvmField
	protected var refreshLayout: SwipeRefreshLayout? = null
    protected var firstRun = false
    @JvmField
	protected var searchItem: MenuItem? = null
    protected var searchView: SearchView? = null

    init {
        supportTag = TAG_INC++
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        if (bundle != null) {
            val name = bundle.getString(Constants.FRAGMENT_NAME)
            if (name != null) {
                title = name
            }
        }
        firstRun = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (title != null) {
            outState.putString(Constants.FRAGMENT_NAME, title.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        if (firstRun) {
            firstRun = false
        } else {
            UpdateView.triggerUpdate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        context = activity as SubsonicActivity
    }

    fun setContext(context: SubsonicActivity?) {
        this.context = context
    }

    protected fun onFinishSetupOptionsMenu(menu: Menu) {
        searchItem = menu.findItem(R.id.menu_global_search)
        if (searchItem != null) {
            searchView = MenuItemCompat.getActionView(searchItem) as SearchView
            val searchManager = requireContext().getSystemService(Context.SEARCH_SERVICE) as SearchManager
            val searchableInfo = searchManager.getSearchableInfo(
                act.componentName
            )
            if (searchableInfo == null) {
                Log.w(TAG, "Failed to get SearchableInfo")
            } else {
                searchView!!.setSearchableInfo(searchableInfo)
            }
            val currentQuery = currentQuery
            if (currentQuery != null) {
                searchView!!.setOnSearchClickListener {
                    searchView!!.setQuery(
                        this@SubsonicFragment.currentQuery,
                        false
                    )
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_global_shuffle -> {
                onShuffleRequested()
                return true
            }

            R.id.menu_exit -> {
                exit()
                return true
            }

            R.id.menu_refresh -> {
                refresh()
                return true
            }

            R.id.menu_play_now -> {
                playNow(false, false)
                return true
            }

            R.id.menu_play_last -> {
                playNow(false, true)
                return true
            }

            R.id.menu_play_next -> {
                playNow(false, true, true)
                return true
            }

            R.id.menu_shuffle -> {
                playNow(true, false)
                return true
            }

            R.id.menu_download -> {
                downloadBackground(false)
                clearSelected()
                return true
            }

            R.id.menu_cache -> {
                downloadBackground(true)
                clearSelected()
                return true
            }

            R.id.menu_delete -> {
                delete()
                clearSelected()
                return true
            }

            R.id.menu_add_playlist -> {
                val songs = selectedEntries.filterNotNull().toMutableList()
                addToPlaylist(songs)
                clearSelected()
                return true
            }

            R.id.menu_star, R.id.menu_unstar -> {
                toggleSelectedStarred()
                return true
            }
        }
        return false
    }

    fun onCreateContextMenuSupport(
        menu: Menu,
        menuInflater: MenuInflater,
        updateView: UpdateView<*>?,
        selected: Any?
    ) {
        if (selected is MusicDirectory.Entry) {
            val entry = selected
            if (entry is PodcastEpisode) {
                if (isOffline(requireContext())) {
                    if (entry.isVideo) {
                        menuInflater.inflate(R.menu.select_video_context_offline, menu)
                    } else {
                        menuInflater.inflate(R.menu.select_podcast_episode_context_offline, menu)
                    }
                } else {
                    if (entry.isVideo) {
                        menuInflater.inflate(R.menu.select_podcast_episode_video_context, menu)
                    } else {
                        menuInflater.inflate(R.menu.select_podcast_episode_context, menu)
                    }
                    if (entry.bookmark == null) {
                        menu.removeItem(R.id.bookmark_menu_delete)
                    }
                    if (UserUtil.canPodcast()) {
                        val status = entry.status
                        if ("completed" == status) {
                            menu.removeItem(R.id.song_menu_server_download)
                        }
                    } else {
                        menu.removeItem(R.id.song_menu_server_download)
                        menu.removeItem(R.id.song_menu_server_delete)
                    }
                }
            } else if (entry.isDirectory) {
                if (isOffline(requireContext())) {
                    menuInflater.inflate(R.menu.select_album_context_offline, menu)
                } else {
                    menuInflater.inflate(R.menu.select_album_context, menu)
                    if (isTagBrowsing(requireContext())) {
                        menu.removeItem(R.id.menu_rate)
                    }
                }
            } else if (!entry.isVideo) {
                if (isOffline(requireContext())) {
                    menuInflater.inflate(R.menu.select_song_context_offline, menu)
                } else {
                    menuInflater.inflate(R.menu.select_song_context, menu)
                    if (entry.bookmark == null) {
                        menu.removeItem(R.id.bookmark_menu_delete)
                    }
                    val songPressAction = getSongPressAction(
                        requireContext()
                    )
                    if ("next" != songPressAction && "last" != songPressAction) {
                        menu.setGroupVisible(R.id.hide_play_now, false)
                    }
                }
            } else {
                if (isOffline(requireContext())) {
                    menuInflater.inflate(R.menu.select_video_context_offline, menu)
                } else {
                    menuInflater.inflate(R.menu.select_video_context, menu)
                }
            }
            val starMenu =
                menu.findItem(if (entry.isDirectory) R.id.album_menu_star else R.id.song_menu_star)
            starMenu?.setTitle(if (entry.isStarred()) R.string.common_unstar else R.string.common_star)
            if (!isShowArtistEnabled || !isTagBrowsing(requireContext()) && entry.parent == null || isTagBrowsing(
                    requireContext()
                ) && entry.artistId == null
            ) {
                menu.setGroupVisible(R.id.hide_show_artist, false)
            }
        } else if (selected is Artist) {
            if (isOffline(requireContext())) {
                menuInflater.inflate(R.menu.select_artist_context_offline, menu)
            } else {
                menuInflater.inflate(R.menu.select_artist_context, menu)
                menu.findItem(R.id.artist_menu_star)
                    .setTitle(if (selected.isStarred) R.string.common_unstar else R.string.common_star)
            }
        }
        MenuUtil.hideMenuItems(context, menu, updateView)
    }

    protected fun recreateContextMenu(menu: Menu) {
        val menuItems: MutableList<MenuItem> = ArrayList()
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.isVisible) {
                menuItems.add(item)
            }
        }
        menu.clear()
        for (i in menuItems.indices) {
            val item = menuItems[i]
            menu.add(supportTag, item.itemId, Menu.NONE, item.title)
        }
    }

    // For reverting specific removals: https://github.com/daneren2005/Subsonic/commit/fbd1a68042dfc3601eaa0a9e37b3957bbdd51420
    fun onContextItemSelected(menuItem: MenuItem, selectedItem: Any): Boolean {
        val artist = if (selectedItem is Artist) selectedItem else null
        var entry = if (selectedItem is MusicDirectory.Entry) selectedItem else null
        if (selectedItem is DownloadFile) {
            entry = selectedItem.song
        }
        val songs: MutableList<MusicDirectory.Entry> = ArrayList(1)
        entry?.let { songs.add(it) }
        when (menuItem.itemId) {
            R.id.artist_menu_play_now -> downloadRecursively(
                artist!!.id, false, false, true, false, false
            )

            R.id.artist_menu_play_shuffled -> downloadRecursively(
                artist!!.id,
                false,
                false,
                true,
                true,
                false
            )

            R.id.artist_menu_play_next -> downloadRecursively(
                artist!!.id,
                false,
                true,
                false,
                false,
                false,
                true
            )

            R.id.artist_menu_play_last -> downloadRecursively(
                artist!!.id,
                false,
                true,
                false,
                false,
                false
            )

            R.id.artist_menu_download -> downloadRecursively(
                artist!!.id,
                false,
                true,
                false,
                false,
                true
            )

            R.id.artist_menu_pin -> downloadRecursively(artist!!.id, true, true, false, false, true)
            R.id.artist_menu_delete -> deleteRecursively(artist)
            R.id.artist_menu_star -> UpdateHelper.toggleStarred(context, artist)
            R.id.album_menu_play_now -> {
                artistOverride = true
                downloadRecursively(entry!!.id, false, false, true, false, false)
            }

            R.id.album_menu_play_shuffled -> {
                artistOverride = true
                downloadRecursively(entry!!.id, false, false, true, true, false)
            }

            R.id.album_menu_play_next -> {
                artistOverride = true
                downloadRecursively(entry!!.id, false, true, false, false, false, true)
            }

            R.id.album_menu_play_last -> {
                artistOverride = true
                downloadRecursively(entry!!.id, false, true, false, false, false)
            }

            R.id.album_menu_download -> {
                artistOverride = true
                downloadRecursively(entry!!.id, false, true, false, false, true)
            }

            R.id.album_menu_pin -> {
                artistOverride = true
                downloadRecursively(entry!!.id, true, true, false, false, true)
            }

            R.id.album_menu_star -> UpdateHelper.toggleStarred(context, entry)
            R.id.album_menu_delete -> deleteRecursively(entry)
            R.id.album_menu_info -> displaySongInfo(entry)
            R.id.album_menu_show_artist -> showAlbumArtist(selectedItem as MusicDirectory.Entry)
            R.id.album_menu_share -> createShare(songs)
            R.id.song_menu_play_now -> playNow(songs)
            R.id.song_menu_play_next -> downloadService!!.download(songs, false, false, true, false)
            R.id.song_menu_play_last -> downloadService!!.download(
                songs,
                false,
                false,
                false,
                false
            )

            R.id.song_menu_download -> downloadService!!.downloadBackground(songs, false)
            R.id.song_menu_pin -> downloadService!!.downloadBackground(songs, true)
            R.id.song_menu_delete -> deleteSongs(songs)
            R.id.song_menu_add_playlist -> addToPlaylist(songs)
            R.id.song_menu_star -> UpdateHelper.toggleStarred(context, entry)
            R.id.song_menu_play_external -> playExternalPlayer(entry)
            R.id.song_menu_info -> displaySongInfo(entry)
            R.id.song_menu_stream_external -> streamExternalPlayer(entry)
            R.id.song_menu_share -> createShare(songs)
            R.id.song_menu_show_album -> showAlbum(selectedItem as MusicDirectory.Entry)
            R.id.song_menu_show_artist -> showArtist(selectedItem as MusicDirectory.Entry)
            R.id.song_menu_server_download -> downloadPodcastEpisode(entry as PodcastEpisode?)
            R.id.song_menu_server_delete -> deletePodcastEpisode(entry as PodcastEpisode?)
            R.id.bookmark_menu_delete -> deleteBookmark<Any>(entry, null)
            R.id.menu_rate -> UpdateHelper.setRating(context, entry)
            else -> return false
        }
        return true
    }

    @JvmOverloads
    fun replaceFragment(fragment: SubsonicFragment, replaceCurrent: Boolean = true) {
        act.replaceFragment(
            fragment,
            fragment.supportTag,
            secondaryFragment && replaceCurrent
        )
    }

    fun replaceExistingFragment(fragment: SubsonicFragment) {
        act.replaceExistingFragment(fragment, fragment.supportTag)
    }

    fun removeCurrent() {
        act.removeCurrent()
    }

    val rootId: Int
        get() = rootView!!.id

    fun setSupportTag(tag: String) {
        supportTag = tag.toInt()
    }

    open fun setPrimaryFragment(primary: Boolean) {
        primaryFragment = primary
        if (primary) {
            if (context != null && title != null) {
                act.title = title!!
                act.setSubtitle(subtitle)
            }
            if (invalidated) {
                invalidated = false
                refresh(false)
            }
        }
    }

    fun setPrimaryFragment(primary: Boolean, secondary: Boolean) {
        setPrimaryFragment(primary)
        secondaryFragment = secondary
    }

    fun setSecondaryFragment(secondary: Boolean) {
        secondaryFragment = secondary
    }

    open fun setIsOnlyVisible(isOnlyVisible: Boolean) {
        this.isOnlyVisible = isOnlyVisible
    }

    fun invalidate() {
        if (primaryFragment) {
            refresh(true)
        } else {
            invalidated = true
        }
    }

    val downloadService: DownloadService?
        get() = if (context != null) act.downloadService else null

    protected fun refresh() {
        refresh(true)
    }

    protected open fun refresh(refresh: Boolean) {}
    override fun onRefresh() {
        refreshLayout!!.isRefreshing = false
        refresh()
    }

    protected fun exit() {
        if ((context as Any?)!!.javaClass != SubsonicFragmentActivity::class.java) {
            val intent = Intent(context, SubsonicFragmentActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            intent.putExtra(Constants.INTENT_EXTRA_NAME_EXIT, true)
            startActivityWithoutTransition(act, intent)
        } else {
            requireContext().stopService(Intent(context, DownloadService::class.java))
            act.finish()
        }
    }

    fun setProgressVisible(visible: Boolean) {
        val view = rootView!!.findViewById<View>(R.id.tab_progress)
        if (view != null) {
            view.visibility = if (visible) View.VISIBLE else View.GONE
            if (visible) {
                val progress = rootView!!.findViewById<View>(R.id.tab_progress_spinner)
                progress.visibility = View.VISIBLE
            }
        }
    }

    fun updateProgress(message: String?) {
        val view = rootView!!.findViewById<View>(R.id.tab_progress_message) as TextView
        if (view != null) {
            view.text = message
        }
    }

    open fun setEmpty(empty: Boolean) {
        val view = rootView!!.findViewById<View>(R.id.tab_progress)
        if (empty) {
            view.visibility = View.VISIBLE
            val progress = view.findViewById<View>(R.id.tab_progress_spinner)
            progress.visibility = View.GONE
            val text = view.findViewById<View>(R.id.tab_progress_message) as TextView
            text.setText(R.string.common_empty)
        } else {
            view.visibility = View.GONE
        }
    }

    @get:Synchronized
    protected val imageLoader: ImageLoader?
        protected get() = act.imageLoader

    fun setTitle(title: CharSequence?) {
        this.title = title
        act.title = title!!
    }

    open fun setTitle(title: Int) {
        this.title = requireContext().resources.getString(title)
        act.setTitle(this.title as String)
    }

    open fun setSubtitle(title: CharSequence?) {
        subtitle = title
        act.setSubtitle(title)
    }

    fun getTitle(): CharSequence? {
        return title
    }

    protected fun setupScrollList(listView: AbsListView) {
        if (!act.isTouchscreen) {
            refreshLayout!!.isEnabled = false
        } else {
            listView.setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {}
                override fun onScroll(
                    view: AbsListView,
                    firstVisibleItem: Int,
                    visibleItemCount: Int,
                    totalItemCount: Int
                ) {
                    val topRowVerticalPosition =
                        if (listView.childCount == 0) 0 else listView.getChildAt(0).top
                    refreshLayout!!.isEnabled =
                        topRowVerticalPosition >= 0 && listView.firstVisiblePosition == 0
                }
            })
            refreshLayout!!.setColorSchemeResources(
                R.color.holo_blue_light,
                R.color.holo_orange_light,
                R.color.holo_green_light,
                R.color.holo_red_light
            )
        }
    }

    protected fun setupScrollList(recyclerView: RecyclerView) {
        if (!act.isTouchscreen) {
            refreshLayout!!.isEnabled = false
        } else {
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    refreshLayout!!.isEnabled = !recyclerView.canScrollVertically(-1)
                }
            })
            refreshLayout!!.setColorSchemeResources(
                R.color.holo_blue_light,
                R.color.holo_orange_light,
                R.color.holo_green_light,
                R.color.holo_red_light
            )
        }
    }

    fun setupLayoutManager(recyclerView: RecyclerView, largeAlbums: Boolean) {
        recyclerView.layoutManager = getLayoutManager(recyclerView, largeAlbums)
    }

    fun getLayoutManager(
        recyclerView: RecyclerView,
        largeCells: Boolean
    ): RecyclerView.LayoutManager {
        return if (largeCells) {
            getGridLayoutManager(recyclerView)
        } else {
            linearLayoutManager
        }
    }

    fun getGridLayoutManager(recyclerView: RecyclerView): GridLayoutManager {
        val columns = recyclerColumnCount
        val gridLayoutManager = GridLayoutManager(context, columns)
        val spanSizeLookup = getSpanSizeLookup(gridLayoutManager)
        if (spanSizeLookup != null) {
            gridLayoutManager.spanSizeLookup = spanSizeLookup
        }
        val itemDecoration = itemDecoration
        if (itemDecoration != null) {
            recyclerView.addItemDecoration(itemDecoration)
        }
        return gridLayoutManager
    }

    val linearLayoutManager: LinearLayoutManager
        get() {
            val layoutManager = LinearLayoutManager(context)
            layoutManager.orientation = LinearLayoutManager.VERTICAL
            return layoutManager
        }

    open fun getSpanSizeLookup(gridLayoutManager: GridLayoutManager): SpanSizeLookup? {
        return object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val adapter = currentAdapter
                return if (adapter != null) {
                    val viewType = adapter.getItemViewType(position)
                    if (viewType == SectionAdapter.VIEW_TYPE_HEADER) {
                        gridLayoutManager.spanCount
                    } else {
                        1
                    }
                } else {
                    1
                }
            }
        }
    }

    val itemDecoration: ItemDecoration
        get() = GridSpacingDecoration()
    val recyclerColumnCount: Int
        get() = if (isOnlyVisible) {
            requireContext().resources.getInteger(R.integer.Grid_FullScreen_Columns)
        } else {
            requireContext().resources.getInteger(R.integer.Grid_Columns)
        }

    protected fun warnIfStorageUnavailable() {
        if (!isExternalStoragePresent) {
            toast(requireContext(), R.string.select_album_no_sdcard)
        }
        try {
            val stat = StatFs(FileUtil.getMusicDirectory(context).path)
            val bytesAvailableFs = stat.availableBlocks.toLong() * stat.blockSize.toLong()
            if (bytesAvailableFs < 50000000L) {
                toast(
                    context,
                    requireContext().resources.getString(
                        R.string.select_album_no_room,
                        formatBytes(bytesAvailableFs)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error while checking storage space for music directory", e)
        }
    }

    protected fun onShuffleRequested() {
        if (isOffline(requireContext())) {
            val downloadService = downloadService ?: return
            downloadService.clear()
            downloadService.isShufflePlayEnabled = true
            act.openNowPlaying()
            return
        }
        val dialogView = act.layoutInflater.inflate(R.layout.shuffle_dialog, null)
        val startYearBox = dialogView.findViewById<View>(R.id.start_year) as EditText
        val endYearBox = dialogView.findViewById<View>(R.id.end_year) as EditText
        val genreBox = dialogView.findViewById<View>(R.id.genre) as EditText
        val genreCombo = dialogView.findViewById<View>(R.id.genre_combo) as Button
        val prefs = getPreferences(
            requireContext()
        )
        val oldStartYear = prefs.getString(Constants.PREFERENCES_KEY_SHUFFLE_START_YEAR, "")
        val oldEndYear = prefs.getString(Constants.PREFERENCES_KEY_SHUFFLE_END_YEAR, "")
        val oldGenre = prefs.getString(Constants.PREFERENCES_KEY_SHUFFLE_GENRE, "")
        var _useCombo = false
        if (ServerInfo.checkServerVersion(context, "1.9.0")) {
            genreBox.visibility = View.GONE
            genreCombo.setOnClickListener {
                object : LoadingTask<List<Genre?>?>(context, true) {
                    @Throws(Throwable::class)
                    override fun doInBackground(): List<Genre?>? {
                        val musicService = MusicServiceFactory.getMusicService(context)
                        return musicService.getGenres(false, context, this)
                    }

                    protected override fun done(genres: List<Genre?>?) {
                        val names: MutableList<String> = ArrayList()
                        val blank = context.resources.getString(R.string.select_genre_blank)
                        names.add(blank)
                        if (genres != null) {
                            for (genre in genres) {
                                genre?.let { it1 -> names.add(it1.name) }
                            }
                        }
                        val finalNames: List<String> = names
                        val builder = AlertDialog.Builder(context)
                        builder.setTitle(R.string.shuffle_pick_genre)
                            .setItems(names.toTypedArray<CharSequence>()) { dialog, which ->
                                if (which == 0) {
                                    genreCombo.text = ""
                                } else {
                                    genreCombo.text = finalNames[which]
                                }
                            }
                        val dialog = builder.create()
                        dialog.show()
                    }

                    override fun error(error: Throwable) {
                        val msg: String
                        msg = if (error is OfflineException || error is ServerTooOldException) {
                            getErrorMessage(error)
                        } else {
                            context.resources.getString(R.string.playlist_error) + " " + getErrorMessage(
                                error
                            )
                        }
                        toast(context, msg, false)
                    }
                }.execute()
            }
            _useCombo = true
        } else {
            genreCombo.visibility = View.GONE
        }
        val useCombo = _useCombo
        startYearBox.setText(oldStartYear)
        endYearBox.setText(oldEndYear)
        genreBox.setText(oldGenre)
        genreCombo.text = oldGenre
        val builder = AlertDialog.Builder(
            requireContext()
        )
        builder.setTitle(R.string.shuffle_title)
            .setView(dialogView)
            .setPositiveButton(R.string.common_ok, object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface, id: Int) {
                    val genre: String
                    genre = if (useCombo) {
                        genreCombo.text.toString()
                    } else {
                        genreBox.text.toString()
                    }
                    val startYear = startYearBox.text.toString()
                    val endYear = endYearBox.text.toString()
                    val editor = prefs.edit()
                    editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_START_YEAR, startYear)
                    editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_END_YEAR, endYear)
                    editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_GENRE, genre)
                    editor.commit()
                    val downloadService: DownloadService = downloadService ?: return
                    downloadService.clear()
                    downloadService.isShufflePlayEnabled = true
                    act.openNowPlaying()
                }
            })
            .setNegativeButton(R.string.common_cancel, null)
        val dialog = builder.create()
        dialog.show()
    }

    protected fun downloadRecursively(
        id: String?,
        save: Boolean,
        append: Boolean,
        autoplay: Boolean,
        shuffle: Boolean,
        background: Boolean
    ) {
        downloadRecursively(id, "", true, save, append, autoplay, shuffle, background)
    }

    protected fun downloadRecursively(
        id: String?,
        save: Boolean,
        append: Boolean,
        autoplay: Boolean,
        shuffle: Boolean,
        background: Boolean,
        playNext: Boolean
    ) {
        downloadRecursively(id, "", true, save, append, autoplay, shuffle, background, playNext)
    }

    protected fun downloadPlaylist(
        id: String?,
        name: String?,
        save: Boolean,
        append: Boolean,
        autoplay: Boolean,
        shuffle: Boolean,
        background: Boolean
    ) {
        downloadRecursively(id, name, false, save, append, autoplay, shuffle, background)
    }

    protected fun downloadRecursively(
        id: String?,
        name: String?,
        isDirectory: Boolean,
        save: Boolean,
        append: Boolean,
        autoplay: Boolean,
        shuffle: Boolean,
        background: Boolean,
        playNext: Boolean = false
    ) {
        object : RecursiveLoader(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Boolean {
                musicService = MusicServiceFactory.getMusicService(context)
                val root: MusicDirectory
                root = if (share != null) {
                    share!!.musicDirectory
                } else if (isDirectory) {
                    if (id != null) {
                        getMusicDirectory(id, name, false, musicService, this)
                    } else {
                        musicService.getStarredList(context, this)
                    }
                } else {
                    musicService.getPlaylist(true, id, name, context, this)
                }
                val shuffleByAlbum = getPreferences(context).getBoolean(
                    Constants.PREFERENCES_KEY_SHUFFLE_BY_ALBUM,
                    true
                )
                if (shuffle && shuffleByAlbum) {
                    Collections.shuffle(root.getChildren())
                }
                songs = mutableListOf()
                getSongsRecursively(root, songs)
                if (shuffle && !shuffleByAlbum) {
                    Collections.shuffle(songs)
                }
                var transition = false
                if (!songs.isEmpty() && downloadService != null) {
                    // Conditions for a standard play now operation
                    if (!append && !save && autoplay && !playNext && !shuffle && !background) {
                        playNowOverride = true
                        return false
                    }
                    if (!append && !background) {
                        downloadService?.clear()
                    }
                    if (!background) {
                        downloadService?.download(songs, save, autoplay, playNext, false)
                        if (!append) {
                            transition = true
                        }
                    } else {
                        downloadService?.downloadBackground(songs, save)
                    }
                }
                artistOverride = false
                return transition
            }
        }.execute()
    }

    protected fun downloadRecursively(
        albums: List<MusicDirectory.Entry>,
        shuffle: Boolean,
        append: Boolean,
        playNext: Boolean
    ) {
        object : RecursiveLoader(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Boolean {
                musicService = MusicServiceFactory.getMusicService(context)
                if (shuffle) {
                    Collections.shuffle(albums)
                }
                songs = LinkedList()
                val root = MusicDirectory()
                root.addChildren(albums)
                getSongsRecursively(root, songs)
                var transition = false
                if (!songs.isEmpty() && downloadService != null) {
                    // Conditions for a standard play now operation
                    if (!append && !shuffle) {
                        playNowOverride = true
                        return false
                    }
                    if (!append) {
                        downloadService?.clear()
                    }
                    downloadService?.download(songs, false, true, playNext, false)
                    if (!append) {
                        transition = true
                    }
                }
                artistOverride = false
                return transition
            }
        }.execute()
    }

    @Throws(Exception::class)
    protected fun getMusicDirectory(
        id: String?,
        name: String?,
        refresh: Boolean,
        service: MusicService,
        listener: ProgressListener?
    ): MusicDirectory {
        return getMusicDirectory(id, name, refresh, false, service, listener)
    }

    @Throws(Exception::class)
    protected fun getMusicDirectory(
        id: String?,
        name: String?,
        refresh: Boolean,
        forceArtist: Boolean,
        service: MusicService,
        listener: ProgressListener?
    ): MusicDirectory {
        return if (isTagBrowsing(requireContext()) && !isOffline(
                requireContext()
            )
        ) {
            if (artist && !artistOverride || forceArtist) {
                service.getArtist(id, name, refresh, context, listener)
            } else {
                service.getAlbum(id, name, refresh, context, listener)
            }
        } else {
            service.getMusicDirectory(id, name, refresh, context, listener)
        }
    }

    protected fun addToPlaylist(songs: MutableList<MusicDirectory.Entry>) {
        val it = songs.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry!!.isDirectory) {
                it.remove()
            }
        }
        if (songs.isEmpty()) {
            toast(context, "No songs selected")
            return
        }
        object : LoadingTask<List<Playlist>>(context, true) {
            @Throws(Throwable::class)
            override fun doInBackground(): List<Playlist> {
                val musicService = MusicServiceFactory.getMusicService(context)
                val playlists: MutableList<Playlist> = ArrayList()
                playlists.addAll(musicService.getPlaylists(false, context, this))

                // Iterate through and remove all non owned public playlists
                val it = playlists.iterator()
                while (it.hasNext()) {
                    val playlist = it.next()
                    if (playlist.public == true && playlist.id.indexOf(".m3u") == -1 && UserUtil.getCurrentUsername(
                            context
                        ) != playlist.owner
                    ) {
                        it.remove()
                    }
                }
                return playlists
            }

            protected override fun done(playlists: List<Playlist>) {
                // Create adapter to show playlists
                val createNew =
                    Playlist("-1", context.resources.getString(R.string.playlist_create_new))
                val playlistAdapter: ArrayAdapter<*> = object :
                    ArrayAdapter<Playlist>(context, R.layout.basic_count_item, playlists.toMutableList().apply { add(0, createNew) }) {
                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {
                        val playlist = getItem(position)

                        // Create new if not getting a convert view to use
                        val view: PlaylistSongView
                        view = if (convertView is PlaylistSongView) {
                            convertView
                        } else {
                            PlaylistSongView(context)
                        }
                        view.setObject(playlist, songs)
                        return view
                    }
                }
                val builder = AlertDialog.Builder(context)
                builder.setTitle(R.string.playlist_add_to)
                    .setAdapter(playlistAdapter) { dialog, which ->
                        if (which > 0) {
                            addToPlaylist(playlists[which], songs)
                        } else {
                            createNewPlaylist(songs, false)
                        }
                    }
                val dialog = builder.create()
                dialog.show()
            }

            override fun error(error: Throwable) {
                val msg: String
                msg = if (error is OfflineException || error is ServerTooOldException) {
                    getErrorMessage(error)
                } else {
                    context.resources.getString(R.string.playlist_error) + " " + getErrorMessage(
                        error
                    )
                }
                toast(context, msg, false)
            }
        }.execute()
    }

    private fun addToPlaylist(playlist: Playlist, songs: List<MusicDirectory.Entry?>) {
        object : SilentBackgroundTask<Void?>(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                val musicService = MusicServiceFactory.getMusicService(context)
                musicService.addToPlaylist(playlist.id, songs, context, null)
                return null
            }

            protected override fun done(result: Void?) {
                toast(
                    context,
                    context.resources.getString(
                        R.string.updated_playlist,
                        songs.size.toString(),
                        playlist.name
                    )
                )
            }

            override fun error(error: Throwable) {
                val msg: String
                msg = if (error is OfflineException || error is ServerTooOldException) {
                    getErrorMessage(error)
                } else {
                    context.resources.getString(
                        R.string.updated_playlist_error,
                        playlist.name
                    ) + " " + getErrorMessage(error)
                }
                toast(context, msg, false)
            }
        }.execute()
    }

    protected fun createNewPlaylist(songs: List<MusicDirectory.Entry?>, getSuggestion: Boolean) {
        val layout = act.layoutInflater.inflate(R.layout.save_playlist, null)
        val playlistNameView = layout.findViewById<View>(R.id.save_playlist_name) as EditText
        val overwriteCheckBox = layout.findViewById<View>(R.id.save_playlist_overwrite) as CheckBox
        if (getSuggestion) {
            val downloadService = downloadService
            var playlistName: String? = null
            var playlistId: String? = null
            if (downloadService != null) {
                playlistName = downloadService.suggestedPlaylistName
                playlistId = downloadService.suggestedPlaylistId
            }
            if (playlistName != null) {
                playlistNameView.setText(playlistName)
                if (playlistId != null) {
                    try {
                        if (ServerInfo.checkServerVersion(
                                context,
                                "1.8.0"
                            ) && playlistId.toInt() != -1
                        ) {
                            overwriteCheckBox.isChecked = true
                            overwriteCheckBox.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        Log.i(TAG, "Playlist id isn't a integer, probably MusicCabinet", e)
                    }
                }
            } else {
                val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd")
                playlistNameView.setText(dateFormat.format(Date()))
            }
        } else {
            val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd")
            playlistNameView.setText(dateFormat.format(Date()))
        }
        val builder = AlertDialog.Builder(
            requireContext()
        )
        builder.setTitle(R.string.download_playlist_title)
            .setMessage(R.string.download_playlist_name)
            .setView(layout)
            .setPositiveButton(R.string.common_save, object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface, id: Int) {
                    val playlistName = playlistNameView.text.toString()
                    if (overwriteCheckBox.isChecked) {
                        downloadService?.let {
                            overwritePlaylist(
                                songs,
                                playlistName,
                                it.suggestedPlaylistId
                            )
                        }
                    } else {
                        createNewPlaylist(songs, playlistName)
                        if (getSuggestion) {
                            downloadService?.setSuggestedPlaylistName(playlistName, null)
                        }
                    }
                }
            })
            .setNegativeButton(R.string.common_cancel) { dialog, id -> dialog.cancel() }
            .setCancelable(true)
        val dialog = builder.create()
        dialog.show()
    }

    private fun createNewPlaylist(songs: List<MusicDirectory.Entry?>, name: String) {
        object : SilentBackgroundTask<Void?>(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                val musicService = MusicServiceFactory.getMusicService(context)
                musicService.createPlaylist(null, name, songs, context, null)
                return null
            }

            protected override fun done(result: Void?) {
                toast(context, R.string.download_playlist_done)
            }

            override fun error(error: Throwable) {
                val msg =
                    context.resources.getString(R.string.download_playlist_error) + " " + getErrorMessage(
                        error
                    )
                Log.e(TAG, "Failed to create playlist", error)
                toast(context, msg)
            }
        }.execute()
    }

    private fun overwritePlaylist(songs: List<MusicDirectory.Entry?>, name: String, id: String) {
        object : SilentBackgroundTask<Void?>(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                val musicService = MusicServiceFactory.getMusicService(context)
                val playlist = musicService.getPlaylist(true, id, name, context, null)
                val toDelete = playlist.getChildren()
                musicService.overwritePlaylist(id, name, toDelete.size, songs, context, null)
                return null
            }

            protected override fun done(result: Void?) {
                toast(context, R.string.download_playlist_done)
            }

            override fun error(error: Throwable) {
                val msg: String
                msg = if (error is OfflineException || error is ServerTooOldException) {
                    getErrorMessage(error)
                } else {
                    context.resources.getString(R.string.download_playlist_error) + " " + getErrorMessage(
                        error
                    )
                }
                Log.e(TAG, "Failed to overwrite playlist", error)
                toast(context, msg, false)
            }
        }.execute()
    }

    fun displaySongInfo(song: MusicDirectory.Entry?) {
        var duration: Int? = null
        var bitrate: Int? = null
        var format: String? = null
        var size: Long = 0
        if (!song!!.isDirectory) {
            try {
                val downloadFile = DownloadFile(context, song, false)
                val file = downloadFile.completeFile
                if (file.exists()) {
                    val metadata = MediaMetadataRetriever()
                    metadata.setDataSource(file.absolutePath)
                    var tmp = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    duration = (tmp ?: "0").toInt() / 1000
                    format = FileUtil.getExtension(file.name)
                    size = file.length()

                    // If no duration try to read bitrate tag
                    if (duration == null) {
                        tmp = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        bitrate = (tmp ?: "0").toInt() / 1000
                    } else {
                        // Otherwise do a calculation for it
                        // Divide by 1000 so in kbps
                        bitrate = (size / duration).toInt() / 1000 * 8
                    }
                    if (isOffline(requireContext())) {
                        song.genre =
                            metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        val year =
                            metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        song.year = (year ?: "0").toInt()
                    }
                }
            } catch (e: Exception) {
                Log.i(TAG, "Device doesn't properly support MediaMetadataRetreiver")
            }
        }
        if (duration == null) {
            duration = song.duration
        }
        val headers: MutableList<Int?> = ArrayList()
        val details: MutableList<String?> = ArrayList()
        if (!song.isDirectory) {
            headers.add(R.string.details_title)
            details.add(song.title)
        }
        if (song is PodcastEpisode) {
            headers.add(R.string.details_podcast)
            details.add(song.artist)
            headers.add(R.string.details_status)
            details.add(song.status)
        } else if (!song.isVideo) {
            if (song.artist != null && "" != song.artist) {
                headers.add(R.string.details_artist)
                details.add(song.artist)
            }
            if (song.album != null && "" != song.album) {
                headers.add(R.string.details_album)
                details.add(song.album)
            }
        }
        if (song.track != null && song.track != 0) {
            headers.add(R.string.details_track)
            details.add(Integer.toString(song.track!!))
        }
        if (song.genre != null && "" != song.genre) {
            headers.add(R.string.details_genre)
            details.add(song.genre)
        }
        if (song.year != null && song.year != 0) {
            headers.add(R.string.details_year)
            details.add(Integer.toString(song.year!!))
        }
        if (!isOffline(requireContext()) && song.suffix != null) {
            headers.add(R.string.details_server_format)
            details.add(song.suffix)
            if (song.bitRate != null && song.bitRate != 0) {
                headers.add(R.string.details_server_bitrate)
                details.add(song.bitRate.toString() + " kbps")
            }
        }
        if (format != null && "" != format) {
            headers.add(R.string.details_cached_format)
            details.add(format)
        }
        if (bitrate != null && bitrate != 0) {
            headers.add(R.string.details_cached_bitrate)
            details.add("$bitrate kbps")
        }
        if (size != 0L) {
            headers.add(R.string.details_size)
            details.add(formatLocalizedBytes(size, requireContext()))
        }
        if (duration != null && duration != 0) {
            headers.add(R.string.details_length)
            details.add(formatDuration(duration))
        }
        if (song.bookmark != null) {
            headers.add(R.string.details_bookmark_position)
            details.add(formatDuration(song.bookmark!!.position / 1000))
        }
        if (song.getRating() != 0) {
            headers.add(R.string.details_rating)
            details.add(song.getRating().toString() + " stars")
        }
        headers.add(R.string.details_starred)
        details.add(formatBoolean(requireContext(), song.isStarred()))
        try {
            val dates = SongDBHandler.getHandler(context).getLastPlayed(song)
            if (dates != null && dates[0] != null && dates[0]!! > 0) {
                headers.add(R.string.details_last_played)
                details.add(formatDate((if (dates[1] != null && dates[1]!! > dates[0]!!) dates[1] else dates[0])!!))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last played", e)
        }
        if (song is PodcastEpisode) {
            headers.add(R.string.details_description)
            details.add(song.album)
        }
        val title: Int
        title = if (song.isDirectory) {
            R.string.details_title_album
        } else if (song is PodcastEpisode) {
            R.string.details_title_podcast
        } else {
            R.string.details_title_song
        }
        showDetailsDialog(requireContext(), title, headers, details)
    }

    protected fun playVideo(entry: MusicDirectory.Entry?) {
        if (entryExists(entry)) {
            playExternalPlayer(entry)
        } else {
            streamExternalPlayer(entry)
        }
    }

    protected fun playWebView(entry: MusicDirectory.Entry?) {
        val maxBitrate = getMaxVideoBitrate(requireContext())
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(
            MusicServiceFactory.getMusicService(context)
                .getVideoUrl(maxBitrate, context, entry!!.id)
        )
        startActivity(intent)
    }

    protected fun playExternalPlayer(entry: MusicDirectory.Entry?) {
        if (!entryExists(entry)) {
            toast(requireContext(), R.string.download_need_download)
        } else {
            // TODO doesn't work on API >26
            val check = DownloadFile(context, entry, false)
            val file = check.completeFile
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.fromFile(file), "video/*")
            intent.putExtra(Intent.EXTRA_TITLE, entry!!.title)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val intents = requireContext().packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (intents != null && intents.size > 0) {
                startActivity(intent)
            } else {
                toast(requireContext(), R.string.download_no_streaming_player)
            }
        }
    }

    protected fun streamExternalPlayer(entry: MusicDirectory.Entry?) {
        val videoPlayerType = getVideoPlayerType(
            requireContext()
        )
        if ("flash" == videoPlayerType) {
            playWebView(entry)
        } else if ("hls" == videoPlayerType) {
            streamExternalPlayer(entry, "hls")
        } else if ("raw" == videoPlayerType) {
            streamExternalPlayer(entry, "raw")
        } else {
            streamExternalPlayer(entry, entry!!.transcodedSuffix)
        }
    }

    protected fun streamExternalPlayer(entry: MusicDirectory.Entry?, format: String?) {
        try {
            val maxBitrate = getMaxVideoBitrate(requireContext())
            val intent = Intent(Intent.ACTION_VIEW)
            if ("hls" == format) {
                intent.setDataAndType(
                    Uri.parse(
                        MusicServiceFactory.getMusicService(context).getHlsUrl(
                            entry!!.id, maxBitrate, context
                        )
                    ), "application/x-mpegURL"
                )
            } else {
                intent.setDataAndType(
                    Uri.parse(
                        MusicServiceFactory.getMusicService(context)
                            .getVideoStreamUrl(format, maxBitrate, context, entry!!.id)
                    ), "video/*"
                )
            }
            intent.putExtra("title", entry.title)
            val intents = requireContext().packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            if (intents != null && intents.size > 0) {
                startActivity(intent)
            } else {
                toast(requireContext(), R.string.download_no_streaming_player)
            }
        } catch (error: Exception) {
            val msg: String?
            msg = if (error is OfflineException || error is ServerTooOldException) {
                error.message
            } else {
                requireContext().resources.getString(R.string.download_no_streaming_player) + " " + error.message
            }
            toast(context, msg, false)
        }
    }

    protected fun entryExists(entry: MusicDirectory.Entry?): Boolean {
        val check = DownloadFile(context, entry, false)
        return check.isCompleteFileAvailable
    }

    fun deleteRecursively(artist: Artist?) {
        deleteRecursively(artist, FileUtil.getArtistDirectory(context, artist))
    }

    fun deleteRecursively(album: MusicDirectory.Entry?) {
        deleteRecursively(album, FileUtil.getAlbumDirectory(context, album))
    }

    fun deleteRecursively(remove: Any?, dir: File?) {
        if (dir == null) {
            return
        }
        object : LoadingTask<Void?>(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                val mediaStore = MediaStoreService(context)
                FileUtil.recursiveDelete(dir, mediaStore)
                return null
            }

            protected override fun done(result: Void?) {
                if (isOffline(context)) {
                    val adapter = currentAdapter as? SectionAdapter<Any>
                    if (adapter != null) {
                        adapter.removeItem(remove)
                    } else {
                        refresh()
                    }
                } else {
                    UpdateView.triggerUpdate()
                }
            }
        }.execute()
    }

    fun deleteSongs(songs: List<MusicDirectory.Entry?>) {
        object : LoadingTask<Void?>(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                downloadService?.delete(songs)
                return null
            }

            protected override fun done(result: Void?) {
                if (isOffline(context)) {
                    val adapter = currentAdapter as? SectionAdapter<Any>
                    if (adapter != null) {
                        for (song in songs) {
                            adapter.removeItem(song)
                        }
                    } else {
                        refresh()
                    }
                } else {
                    UpdateView.triggerUpdate()
                }
            }
        }.execute()
    }

    fun showAlbumArtist(entry: MusicDirectory.Entry) {
        val fragment: SubsonicFragment = SelectDirectoryFragment()
        val args = Bundle()
        if (isTagBrowsing(requireContext())) {
            args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.artistId)
        } else {
            args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.parent)
        }
        args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.artist)
        args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true)
        fragment.arguments = args
        replaceFragment(fragment, true)
    }

    fun showArtist(entry: MusicDirectory.Entry) {
        val fragment: SubsonicFragment = SelectDirectoryFragment()
        val args = Bundle()
        if (isTagBrowsing(requireContext())) {
            args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.artistId)
        } else {
            if (entry.grandParent == null) {
                args.putString(Constants.INTENT_EXTRA_NAME_CHILD_ID, entry.parent)
            } else {
                args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.grandParent)
            }
        }
        args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.artist)
        args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true)
        fragment.arguments = args
        replaceFragment(fragment, true)
    }

    fun showAlbum(entry: MusicDirectory.Entry) {
        val fragment: SubsonicFragment = SelectDirectoryFragment()
        val args = Bundle()
        if (isTagBrowsing(requireContext())) {
            args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.albumId)
        } else {
            args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.parent)
        }
        args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.album)
        fragment.arguments = args
        replaceFragment(fragment, true)
    }

    fun createShare(entries: List<MusicDirectory.Entry?>) {
        object : LoadingTask<List<Share>>(context, true) {
            @Throws(Throwable::class)
            override fun doInBackground(): List<Share> {
                val ids: MutableList<String?> = ArrayList(entries.size)
                for (entry in entries) {
                    ids.add(entry!!.id)
                }
                val musicService = MusicServiceFactory.getMusicService(context)
                return musicService.createShare(ids, null, 0L, context, this)
            }

            protected override fun done(shares: List<Share>) {
                if (shares.size > 0) {
                    val share = shares[0]
                    shareExternal(share)
                } else {
                    toast(context, context.resources.getString(R.string.share_create_error), false)
                }
            }

            override fun error(error: Throwable) {
                val msg: String
                msg = if (error is OfflineException || error is ServerTooOldException) {
                    getErrorMessage(error)
                } else {
                    context.resources.getString(R.string.share_create_error) + " " + getErrorMessage(
                        error
                    )
                }
                Log.e(TAG, "Failed to create share", error)
                toast(context, msg, false)
            }
        }.execute()
    }

    fun shareExternal(share: Share) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, share.url)
        requireContext().startActivity(
            Intent.createChooser(
                intent,
                requireContext().resources.getString(R.string.share_via)
            )
        )
    }

    protected fun playBookmark(
        songs: List<MusicDirectory.Entry?>,
        song: MusicDirectory.Entry,
        playlistName: String? = null,
        playlistId: String? = null
    ) {
        val position = song.bookmark!!.position
        val builder = AlertDialog.Builder(
            requireContext()
        )
        builder.setTitle(R.string.bookmark_resume_title)
            .setMessage(
                resources.getString(
                    R.string.bookmark_resume,
                    song.title,
                    formatDuration(position / 1000)
                )
            )
            .setPositiveButton(R.string.bookmark_action_resume) { dialog, id ->
                playNow(
                    songs,
                    song,
                    position,
                    playlistName,
                    playlistId
                )
            }
            .setNegativeButton(R.string.bookmark_action_start_over) { dialog, id ->
                val oldBookmark = song.bookmark
                song.bookmark = null
                object : SilentBackgroundTask<Void?>(context) {
                    @Throws(Throwable::class)
                    override fun doInBackground(): Void? {
                        val musicService = MusicServiceFactory.getMusicService(context)
                        musicService.deleteBookmark(song, context, null)
                        return null
                    }

                    override fun error(error: Throwable) {
                        song.bookmark = oldBookmark
                        val msg: String
                        msg = if (error is OfflineException || error is ServerTooOldException) {
                            getErrorMessage(error)
                        } else {
                            context.resources.getString(
                                R.string.bookmark_deleted_error,
                                song.title
                            ) + " " + getErrorMessage(error)
                        }
                        toast(context, msg, false)
                    }
                }.execute()
                playNow(songs, 0, playlistName, playlistId)
            }
        val dialog = builder.create()
        dialog.show()
    }

    protected fun onSongPress(
        entries: List<MusicDirectory.Entry>,
        entry: MusicDirectory.Entry?,
        allowPlayAll: Boolean
    ) {
        onSongPress(entries, entry, 0, allowPlayAll)
    }

    protected fun onSongPress(
        entries: List<MusicDirectory.Entry>,
        entry: MusicDirectory.Entry?,
        position: Int = 0,
        allowPlayAll: Boolean = true
    ) {
        val songs: MutableList<MusicDirectory.Entry> = ArrayList()
        val songPressAction = getSongPressAction(
            requireContext()
        )
        if ("all" == songPressAction && allowPlayAll) {
            for (song in entries) {
                if (!song.isDirectory && !song.isVideo) {
                    songs.add(song)
                }
            }
            playNow(songs, entry, position)
        } else if ("next" == songPressAction) {
            downloadService!!.download(Arrays.asList(entry), false, false, true, false)
        } else if ("last" == songPressAction) {
            downloadService!!.download(Arrays.asList(entry), false, false, false, false)
        } else {
            entry?.let { songs.add(it) }
            playNow(songs)
        }
    }

    protected fun playNow(
        entries: List<MusicDirectory.Entry>,
        playlistName: String? = null,
        playlistId: String? = null
    ) {
        object : RecursiveLoader(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Boolean? {
                getSongsRecursively(entries, songs)
                return null
            }

            override fun done(result: Boolean?) {
                var bookmark: MusicDirectory.Entry? = null
                for (entry in songs) {
                    if (entry.bookmark != null) {
                        bookmark = entry
                        break
                    }
                }

                // If no bookmark found, just play from start
                if (bookmark == null) {
                    playNow(songs, 0, playlistName, playlistId)
                } else {
                    // If bookmark found, then give user choice to start from there or to start over
                    playBookmark(songs, bookmark, playlistName, playlistId)
                }
            }
        }.execute()
    }

    protected fun playNow(
        entries: List<MusicDirectory.Entry?>,
        position: Int,
        playlistName: String? = null,
        playlistId: String? = null
    ) {
        val selected = if (entries.isEmpty()) null else entries[0]
        playNow(entries, selected, position, playlistName, playlistId)
    }

    protected fun playNow(
        entries: List<MusicDirectory.Entry?>,
        song: MusicDirectory.Entry?,
        position: Int,
        playlistName: String? = null,
        playlistId: String? = null
    ) {
        object : LoadingTask<Void?>(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                playNowInTask(entries, song, position, playlistName, playlistId)
                return null
            }

            protected override fun done(result: Void?) {
                this@SubsonicFragment.act.openNowPlaying()
            }
        }.execute()
    }

    protected fun playNowInTask(
        entries: List<MusicDirectory.Entry?>,
        song: MusicDirectory.Entry?,
        position: Int,
        playlistName: String? = null,
        playlistId: String? = null
    ) {
        val downloadService = downloadService ?: return
        downloadService.clear()
        downloadService.download(entries, false, true, true, false, entries.indexOf(song), position)
        downloadService.setSuggestedPlaylistName(playlistName, playlistId)
    }

    protected fun<T> deleteBookmark(entry: MusicDirectory.Entry?, adapter: SectionAdapter<T>?) {
        confirmDialog(requireContext(), R.string.bookmark_delete_title, entry!!.title) { dialog, which ->
            val oldBookmark = entry.bookmark
            entry.bookmark = null
            object : LoadingTask<Void?>(context, false) {
                @Throws(Throwable::class)
                override fun doInBackground(): Void? {
                    val musicService = MusicServiceFactory.getMusicService(context)
                    musicService.deleteBookmark(entry, context, null)
                    object :
                        EntryInstanceUpdater(entry, DownloadService.METADATA_UPDATED_BOOKMARK) {
                        override fun update(found: MusicDirectory.Entry) {
                            found.bookmark = null
                        }
                    }.execute()
                    return null
                }

                protected override fun done(result: Void?) {
                    (adapter as? SectionAdapter<Any>)?.removeItem(entry)
                    toast(
                        context,
                        context.resources.getString(R.string.bookmark_deleted, entry.title)
                    )
                }

                override fun error(error: Throwable) {
                    entry.bookmark = oldBookmark
                    val msg: String
                    msg = if (error is OfflineException || error is ServerTooOldException) {
                        getErrorMessage(error)
                    } else {
                        context.resources.getString(
                            R.string.bookmark_deleted_error,
                            entry.title
                        ) + " " + getErrorMessage(error)
                    }
                    toast(context, msg, false)
                }
            }.execute()
        }
    }

    fun downloadPodcastEpisode(episode: PodcastEpisode?) {
        object : LoadingTask<Void?>(context, true) {
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                val musicService = MusicServiceFactory.getMusicService(context)
                musicService.downloadPodcastEpisode(episode!!.episodeId, context, null)
                return null
            }

            protected override fun done(result: Void?) {
                toast(
                    context,
                    context.resources.getString(
                        R.string.select_podcasts_downloading,
                        episode!!.title
                    )
                )
            }

            override fun error(error: Throwable) {
                toast(context, getErrorMessage(error), false)
            }
        }.execute()
    }

    fun deletePodcastEpisode(episode: PodcastEpisode?) {
        confirmDialog(requireContext(), R.string.common_delete, episode!!.title) { dialog, which ->
            object : LoadingTask<Void?>(context, true) {
                @Throws(Throwable::class)
                override fun doInBackground(): Void? {
                    val musicService = MusicServiceFactory.getMusicService(context)
                    musicService.deletePodcastEpisode(
                        episode.episodeId,
                        episode.parent,
                        null,
                        context
                    )
                    if (downloadService != null) {
                        val episodeList: MutableList<MusicDirectory.Entry?> = ArrayList(1)
                        episodeList.add(episode)
                        downloadService?.delete(episodeList)
                    }
                    return null
                }

                protected override fun done(result: Void?) {
                    (currentAdapter as? SectionAdapter<Any>)?.removeItem(episode)
                }

                override fun error(error: Throwable) {
                    Log.w(TAG, "Failed to delete podcast episode", error)
                    toast(context, getErrorMessage(error), false)
                }
            }.execute()
        }
    }

    open val currentAdapter: SectionAdapter<*>?
        get() = null

    fun stopActionMode() {
        val adapter = currentAdapter
        adapter?.stopActionMode()
    }

    protected fun clearSelected() {
        currentAdapter?.clearSelected()
    }

    protected open val selectedEntries: MutableList<MusicDirectory.Entry?>
        protected get() = currentAdapter!!.getSelected() as MutableList<MusicDirectory.Entry?>

    protected fun playNow(shuffle: Boolean, append: Boolean) {
        playNow(shuffle, append, false)
    }

    protected open fun playNow(shuffle: Boolean, append: Boolean, playNext: Boolean) {
        val songs: List<MusicDirectory.Entry> = selectedEntries.filterNotNull()
        if (!songs.isEmpty()) {
            download(songs, append, false, !append, playNext, shuffle)
            clearSelected()
        }
    }

    protected open fun download(
        entries: List<MusicDirectory.Entry>,
        append: Boolean,
        save: Boolean,
        autoplay: Boolean,
        playNext: Boolean,
        shuffle: Boolean
    ) {
        download(entries, append, save, autoplay, playNext, shuffle, null, null)
    }

    protected fun download(
        entries: List<MusicDirectory.Entry>,
        append: Boolean,
        save: Boolean,
        autoplay: Boolean,
        playNext: Boolean,
        shuffle: Boolean,
        playlistName: String?,
        playlistId: String?
    ) {
        val downloadService = downloadService ?: return
        warnIfStorageUnavailable()

        // Conditions for using play now button
        if (!append && !save && autoplay && !playNext && !shuffle) {
            // Call playNow which goes through and tries to use bookmark information
            playNow(entries, playlistName, playlistId)
            return
        }
        val onValid: RecursiveLoader = object : RecursiveLoader(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Boolean? {
                if (!append) {
                    this@SubsonicFragment.downloadService!!.clear()
                }
                getSongsRecursively(entries, songs)
                downloadService.download(songs, save, autoplay, playNext, shuffle)
                if (playlistName != null) {
                    downloadService.setSuggestedPlaylistName(playlistName, playlistId)
                } else {
                    downloadService.setSuggestedPlaylistName(null, null)
                }
                return null
            }

            override fun done(result: Boolean?) {
                if (autoplay) {
                    this@SubsonicFragment.act.openNowPlaying()
                } else if (save) {
                    toast(
                        context,
                        context.resources.getQuantityString(
                            R.plurals.select_album_n_songs_downloading,
                            songs.size,
                            songs.size
                        )
                    )
                } else if (append) {
                    toast(
                        context,
                        context.resources.getQuantityString(
                            R.plurals.select_album_n_songs_added,
                            songs.size,
                            songs.size
                        )
                    )
                }
            }
        }
        executeOnValid(onValid)
    }

    protected open fun executeOnValid(onValid: RecursiveLoader) {
        onValid.execute()
    }

    protected open fun downloadBackground(save: Boolean) {
        val songs: List<MusicDirectory.Entry> = selectedEntries.filterNotNull()
        if (!songs.isEmpty()) {
            downloadBackground(save, songs)
        }
    }

    protected open fun downloadBackground(save: Boolean, entries: List<MusicDirectory.Entry>) {
        if (downloadService == null) {
            return
        }
        warnIfStorageUnavailable()
        object : RecursiveLoader(context) {
            @Throws(Throwable::class)
            override fun doInBackground(): Boolean? {
                getSongsRecursively(entries, true)
                downloadService?.downloadBackground(songs, save)
                return null
            }

            override fun done(result: Boolean?) {
                toast(
                    context,
                    context.resources.getQuantityString(
                        R.plurals.select_album_n_songs_downloading,
                        songs.size,
                        songs.size
                    )
                )
            }
        }.execute()
    }

    protected open fun delete() {
        val songs: List<MusicDirectory.Entry?> = selectedEntries
        if (!songs.isEmpty()) {
            val downloadService = downloadService
            downloadService?.delete(songs)
        }
    }

    protected open fun toggleSelectedStarred() {
        UpdateHelper.toggleStarred(context, selectedEntries)
    }

    protected open val isShowArtistEnabled: Boolean
        protected get() = false
    protected open val currentQuery: String?
        protected get() = null

    abstract inner class RecursiveLoader(context: Activity?) : LoadingTask<Boolean?>(context) {
        @JvmField
		protected var musicService: MusicService
        protected var playNowOverride = false
        @JvmField
		protected var songs: MutableList<MusicDirectory.Entry> = ArrayList()

        init {
            musicService = MusicServiceFactory.getMusicService(context)
        }

        @Throws(Exception::class)
        protected fun getSiblingsRecursively(entry: MusicDirectory.Entry) {
            val parent = MusicDirectory()
            if (isTagBrowsing(context) && !isOffline(context)) {
                parent.id = entry.albumId
            } else {
                parent.id = entry.parent
            }
            if (parent.id == null) {
                songs.add(entry)
            } else {
                val dir = MusicDirectory.Entry(parent.id)
                dir.isDirectory = true
                parent.addChild(dir)
                getSongsRecursively(parent, songs)
            }
        }

        @Throws(Exception::class)
        protected fun getSongsRecursively(entry: List<MusicDirectory.Entry>) {
            getSongsRecursively(entry, false)
        }

        @Throws(Exception::class)
        protected fun getSongsRecursively(
            entry: List<MusicDirectory.Entry>,
            allowVideo: Boolean
        ) {
            getSongsRecursively(entry, songs, allowVideo)
        }

        @Throws(Exception::class)
        protected fun getSongsRecursively(
            entry: List<MusicDirectory.Entry>,
            songs: MutableList<MusicDirectory.Entry>
        ) {
            getSongsRecursively(entry, songs, false)
        }

        @Throws(Exception::class)
        protected fun getSongsRecursively(
            entry: List<MusicDirectory.Entry>,
            songs: MutableList<MusicDirectory.Entry>,
            allowVideo: Boolean
        ) {
            val dir = MusicDirectory()
            dir.addChildren(entry)
            getSongsRecursively(dir, songs, allowVideo)
        }

        @Throws(Exception::class)
        protected fun getSongsRecursively(
            parent: MusicDirectory,
            songs: MutableList<MusicDirectory.Entry>
        ) {
            getSongsRecursively(parent, songs, false)
        }

        @Throws(Exception::class)
        protected fun getSongsRecursively(
            parent: MusicDirectory,
            songs: MutableList<MusicDirectory.Entry>,
            allowVideo: Boolean
        ) {
            if (songs.size > MAX_SONGS) {
                return
            }
            for (dir in parent.getChildren(true, false)) {
                if (dir!!.getRating() == 1) {
                    continue
                }
                var musicDirectory: MusicDirectory
                musicDirectory = if (isTagBrowsing(context) && !isOffline(context)) {
                    musicService.getAlbum(dir.id, dir.title, false, context, this)
                } else {
                    musicService.getMusicDirectory(dir.id, dir.title, false, context, this)
                }
                getSongsRecursively(musicDirectory, songs)
            }
            for (song in parent.getChildren(false, true)) {
                if ((!song!!.isVideo || allowVideo) && song.getRating() != 1) {
                    songs.add(song)
                }
            }
        }

        protected override fun done(result: Boolean?) {
            warnIfStorageUnavailable()
            if (playNowOverride) {
                playNow(songs)
                return
            }
            if (result == true) {
                this@SubsonicFragment.act.openNowPlaying()
            }
        }

        protected val MAX_SONGS = 500
    }

    companion object {
        private val TAG = SubsonicFragment::class.java.simpleName
        private var TAG_INC = 10
        @JvmField
		protected var random = Random()
        @Synchronized
        fun getStaticImageLoader(context: Context?): ImageLoader? {
            return SubsonicActivity.getStaticImageLoader(context)
        }
    }
}