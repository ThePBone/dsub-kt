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
package github.daneren2005.dsub.activity

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Dialog
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import github.daneren2005.dsub.R
import github.daneren2005.dsub.domain.MusicDirectory
import github.daneren2005.dsub.domain.PlayerQueue
import github.daneren2005.dsub.domain.PlayerState
import github.daneren2005.dsub.domain.ServerInfo
import github.daneren2005.dsub.fragments.DownloadFragment
import github.daneren2005.dsub.fragments.MainFragment
import github.daneren2005.dsub.fragments.NowPlayingFragment
import github.daneren2005.dsub.fragments.SearchFragment
import github.daneren2005.dsub.fragments.SelectArtistFragment
import github.daneren2005.dsub.fragments.SelectBookmarkFragment
import github.daneren2005.dsub.fragments.SelectDirectoryFragment
import github.daneren2005.dsub.fragments.SelectPlaylistFragment
import github.daneren2005.dsub.fragments.SelectPodcastsFragment
import github.daneren2005.dsub.fragments.SelectShareFragment
import github.daneren2005.dsub.fragments.SubsonicFragment
import github.daneren2005.dsub.service.DownloadFile
import github.daneren2005.dsub.service.DownloadService
import github.daneren2005.dsub.service.DownloadService.OnSongChangedListener
import github.daneren2005.dsub.service.MusicServiceFactory
import github.daneren2005.dsub.updates.Updater
import github.daneren2005.dsub.util.Constants
import github.daneren2005.dsub.util.DrawableTint
import github.daneren2005.dsub.util.FileUtil
import github.daneren2005.dsub.util.KeyStoreUtil
import github.daneren2005.dsub.util.SilentBackgroundTask
import github.daneren2005.dsub.util.UserUtil
import github.daneren2005.dsub.util.Util
import github.daneren2005.dsub.view.ChangeLog
import java.io.File
import java.util.Date
import java.util.Locale

/**
 * Created by Scott on 10/14/13.
 */
class SubsonicFragmentActivity : SubsonicActivity(), OnSongChangedListener {
    private var slideUpPanel: SlidingUpPanelLayout? = null
    private var panelSlideListener: SlidingUpPanelLayout.PanelSlideListener? = null
    private var isPanelClosing = false
    private var nowPlayingFragment: NowPlayingFragment? = null
    private var secondaryFragment: SubsonicFragment? = null
    private var mainToolbar: Toolbar? = null
    private var nowPlayingToolbar: Toolbar? = null
    private var bottomBar: View? = null
    private var coverArtView: ImageView? = null
    private var trackView: TextView? = null
    private var artistView: TextView? = null
    private var startButton: ImageButton? = null
    private var lastBackPressTime: Long = 0
    private var currentPlaying: DownloadFile? = null
    private val currentState: PlayerState? = null
    private var previousButton: ImageButton? = null
    private var nextButton: ImageButton? = null
    private var rewindButton: ImageButton? = null
    private var fastforwardButton: ImageButton? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            var fragmentType = intent.getStringExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE)
            var firstRun = false
            if (fragmentType == null) {
                fragmentType = Util.openToTab(this)
                if (fragmentType != null) {
                    firstRun = true
                }
            }
            if ("" == fragmentType || fragmentType == null || firstRun) {
                // Initial startup stuff
                if (!sessionInitialized) {
                    loadSession()
                }
            }
        }
        super.onCreate(savedInstanceState)
        if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_EXIT)) {
            stopService(Intent(this, DownloadService::class.java))
            finish()
            imageLoader?.clearCache()
            DrawableTint.clearCache()
        } else if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD_VIEW)) {
            intent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, "Download")
            lastSelectedPosition = R.id.drawer_downloading
        }
        setContentView(R.layout.abstract_fragment_activity)
        if (findViewById<View?>(R.id.fragment_container) != null && savedInstanceState == null) {
            var fragmentType = intent.getStringExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE)
            if (fragmentType == null) {
                fragmentType = Util.openToTab(this)
                lastSelectedPosition = if (fragmentType != null) {
                    intent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, fragmentType)
                    getDrawerItemId(fragmentType)
                } else {
                    R.id.drawer_home
                }
                val item = drawerList!!.menu.findItem(lastSelectedPosition)
                if (item != null) {
                    item.isChecked = true
                }
            } else {
                lastSelectedPosition = getDrawerItemId(fragmentType)
            }
            currentFragment = getNewFragment(fragmentType)
            if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_ID)) {
                var currentArguments = currentFragment!!.arguments
                if (currentArguments == null) {
                    currentArguments = Bundle()
                }
                currentArguments.putString(
                    Constants.INTENT_EXTRA_NAME_ID, intent.getStringExtra(
                        Constants.INTENT_EXTRA_NAME_ID
                    )
                )
                currentFragment!!.arguments = currentArguments
            }
            currentFragment!!.setPrimaryFragment(true)
            supportFragmentManager.beginTransaction().add(
                R.id.fragment_container,
                currentFragment!!,
                currentFragment!!.supportTag.toString() + ""
            ).commit()
            if (intent.getStringExtra(Constants.INTENT_EXTRA_NAME_QUERY) != null) {
                val fragment = SearchFragment()
                replaceFragment(fragment, fragment.supportTag)
            }

            // If a album type is set, switch to that album type view
            val albumType = intent.getStringExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE)
            if (albumType != null) {
                val fragment: SubsonicFragment = SelectDirectoryFragment()
                val args = Bundle()
                args.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, albumType)
                args.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 20)
                args.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0)
                fragment.arguments = args
                replaceFragment(fragment, fragment.supportTag)
            }
        }
        slideUpPanel = findViewById<View>(R.id.slide_up_panel) as SlidingUpPanelLayout
        panelSlideListener = object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) {}
            override fun onPanelCollapsed(panel: View) {
                isPanelClosing = false
                if (bottomBar!!.visibility == View.GONE) {
                    bottomBar!!.visibility = View.VISIBLE
                    nowPlayingToolbar!!.visibility = View.GONE
                    nowPlayingFragment!!.setPrimaryFragment(false)
                    setSupportActionBar(mainToolbar)
                }
            }

            override fun onPanelExpanded(panel: View) {
                isPanelClosing = false
                currentFragment!!.stopActionMode()

                // Disable custom view before switching
                supportActionBar!!.setDisplayShowCustomEnabled(false)
                supportActionBar!!.setDisplayShowTitleEnabled(true)
                bottomBar!!.visibility = View.GONE
                nowPlayingToolbar!!.visibility = View.VISIBLE
                setSupportActionBar(nowPlayingToolbar)
                if (secondaryFragment == null) {
                    nowPlayingFragment!!.setPrimaryFragment(true)
                } else {
                    secondaryFragment!!.setPrimaryFragment(true)
                }
                drawerToggle!!.isDrawerIndicatorEnabled = false
                supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            }

            override fun onPanelAnchored(panel: View) {}
            override fun onPanelHidden(panel: View) {}
        }
        slideUpPanel!!.setPanelSlideListener(panelSlideListener)
        if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD)) {
            // Post this later so it actually runs
            handler.postDelayed({ openNowPlaying() }, 200)
            intent.removeExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD)
        }
        bottomBar = findViewById<View>(R.id.bottom_bar).apply {
            coverArtView = findViewById<View>(R.id.album_art) as ImageView
            trackView = findViewById<View>(R.id.track_name) as TextView
            artistView = findViewById<View>(R.id.artist_name) as TextView
        }
        mainToolbar = findViewById<View>(R.id.main_toolbar) as Toolbar
        nowPlayingToolbar = findViewById<View>(R.id.now_playing_toolbar) as Toolbar

        setSupportActionBar(mainToolbar)
        if (findViewById<View?>(R.id.fragment_container) != null && savedInstanceState == null) {
            nowPlayingFragment = NowPlayingFragment()
            val trans = supportFragmentManager.beginTransaction()
            trans.add(
                R.id.now_playing_fragment_container,
                nowPlayingFragment!!,
                nowPlayingFragment!!.tag + ""
            )
            trans.commit()
        }
        rewindButton = findViewById<View>(R.id.download_rewind) as ImageButton
        rewindButton!!.setOnClickListener {
            object : SilentBackgroundTask<Void?>(this@SubsonicFragmentActivity) {
                @Throws(Throwable::class)
                override fun doInBackground(): Void? {
                    downloadService?.rewind()
                    return null
                }
            }.execute()
        }
        previousButton = findViewById<View>(R.id.download_previous) as ImageButton
        previousButton!!.setOnClickListener {
            object : SilentBackgroundTask<Void?>(this@SubsonicFragmentActivity) {
                @Throws(Throwable::class)
                override fun doInBackground(): Void? {
                    downloadService?.previous()
                    return null
                }
            }.execute()
        }
        startButton = findViewById<View>(R.id.download_start) as ImageButton
        startButton!!.setOnClickListener {
            object : SilentBackgroundTask<Void?>(this@SubsonicFragmentActivity) {
                @Throws(Throwable::class)
                override fun doInBackground(): Void? {
                    val state = downloadService?.playerState
                    if (state == PlayerState.STARTED) {
                        downloadService?.pause()
                    } else if (state == PlayerState.IDLE) {
                        downloadService?.play()
                    } else {
                        downloadService?.start()
                    }
                    return null
                }
            }.execute()
        }
        nextButton = findViewById<View>(R.id.download_next) as ImageButton
        nextButton!!.setOnClickListener {
            object : SilentBackgroundTask<Void?>(this@SubsonicFragmentActivity) {
                @Throws(Throwable::class)
                override fun doInBackground(): Void? {
                    downloadService?.next()
                    return null
                }
            }.execute()
        }
        fastforwardButton = findViewById<View>(R.id.download_fastforward) as ImageButton
        fastforwardButton!!.setOnClickListener {
            object : SilentBackgroundTask<Void?>(this@SubsonicFragmentActivity) {
                @Throws(Throwable::class)
                override fun doInBackground(): Void? {
                    downloadService?.fastForward()
                    return null
                }
            }.execute()
        }
    }

    override fun onPostCreate(bundle: Bundle?) {
        super.onPostCreate(bundle)
        showInfoDialog()
        checkUpdates()
        val changeLog = ChangeLog(this, Util.getPreferences(this))
        if (changeLog.isFirstRun) {
            if (changeLog.isFirstRunEver) {
                changeLog.updateVersionInPreferences()
            } else {
                val log: Dialog? = changeLog.logDialog
                log?.show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (currentFragment != null && intent.getStringExtra(Constants.INTENT_EXTRA_NAME_QUERY) != null) {
            if (slideUpPanel!!.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                closeNowPlaying()
            }
            if (currentFragment is SearchFragment) {
                val query = intent.getStringExtra(Constants.INTENT_EXTRA_NAME_QUERY)
                val autoplay = intent.getBooleanExtra(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false)
                val artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
                val album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
                val title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
                if (query != null) {
                    (currentFragment as SearchFragment).search(
                        query,
                        autoplay,
                        artist,
                        album,
                        title
                    )
                }
                getIntent().removeExtra(Constants.INTENT_EXTRA_NAME_QUERY)
            } else {
                setIntent(intent)
                val fragment = SearchFragment()
                replaceFragment(fragment, fragment.supportTag)
            }
        } else if (intent.getBooleanExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD, false)) {
            if (slideUpPanel!!.panelState != SlidingUpPanelLayout.PanelState.EXPANDED) {
                openNowPlaying()
            }
        } else {
            if (slideUpPanel!!.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                closeNowPlaying()
            }
            setIntent(intent)
        }
        if (drawer != null) {
            drawer!!.closeDrawers()
        }
    }

    public override fun onResume() {
        super.onResume()
        if (intent.hasExtra(Constants.INTENT_EXTRA_VIEW_ALBUM)) {
            val fragment: SubsonicFragment = SelectDirectoryFragment()
            val args = Bundle()
            args.putString(
                Constants.INTENT_EXTRA_NAME_ID,
                intent.getStringExtra(Constants.INTENT_EXTRA_NAME_ID)
            )
            args.putString(
                Constants.INTENT_EXTRA_NAME_NAME,
                intent.getStringExtra(Constants.INTENT_EXTRA_NAME_NAME)
            )
            args.putString(
                Constants.INTENT_EXTRA_SEARCH_SONG,
                intent.getStringExtra(Constants.INTENT_EXTRA_SEARCH_SONG)
            )
            if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_ARTIST)) {
                args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true)
            }
            if (intent.hasExtra(Constants.INTENT_EXTRA_NAME_CHILD_ID)) {
                args.putString(
                    Constants.INTENT_EXTRA_NAME_CHILD_ID,
                    intent.getStringExtra(Constants.INTENT_EXTRA_NAME_CHILD_ID)
                )
            }
            fragment.arguments = args
            replaceFragment(fragment, fragment.supportTag)
            intent.removeExtra(Constants.INTENT_EXTRA_VIEW_ALBUM)
        }
        UserUtil.seedCurrentUser(this)
        createAccount()
        runWhenServiceAvailable {
            downloadService?.addOnSongChangedListener(
                this@SubsonicFragmentActivity,
                true
            )
        }
    }

    public override fun onPause() {
        super.onPause()
        val downloadService = downloadService
        downloadService?.removeOnSongChangeListener(this)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putString(Constants.MAIN_NOW_PLAYING, nowPlayingFragment!!.tag)
        if (secondaryFragment != null) {
            savedInstanceState.putString(
                Constants.MAIN_NOW_PLAYING_SECONDARY,
                secondaryFragment!!.tag
            )
        }
        savedInstanceState.putInt(
            Constants.MAIN_SLIDE_PANEL_STATE,
            slideUpPanel!!.panelState.hashCode()
        )
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val id = savedInstanceState.getString(Constants.MAIN_NOW_PLAYING)
        val fm = supportFragmentManager
        nowPlayingFragment = fm.findFragmentByTag(id) as NowPlayingFragment?
        val secondaryId = savedInstanceState.getString(Constants.MAIN_NOW_PLAYING_SECONDARY)
        if (secondaryId != null) {
            secondaryFragment = fm.findFragmentByTag(secondaryId) as SubsonicFragment?
            nowPlayingFragment!!.setPrimaryFragment(false)
            secondaryFragment!!.setPrimaryFragment(true)
            val trans = supportFragmentManager.beginTransaction()
            trans.hide(nowPlayingFragment!!)
            trans.commit()
        }
        if (drawerToggle != null && backStack.size > 0) {
            drawerToggle!!.isDrawerIndicatorEnabled = false
        }
        if (savedInstanceState.getInt(
                Constants.MAIN_SLIDE_PANEL_STATE,
                -1
            ) == SlidingUpPanelLayout.PanelState.EXPANDED.hashCode()
        ) {
            panelSlideListener!!.onPanelExpanded(null)
        }
    }

    override fun setContentView(viewId: Int) {
        super.setContentView(viewId)
        if (drawerToggle != null) {
            drawerToggle!!.isDrawerIndicatorEnabled = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (slideUpPanel!!.panelState == SlidingUpPanelLayout.PanelState.EXPANDED && secondaryFragment == null) {
            slideUpPanel!!.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        } else if (onBackPressedSupport()) {
            if (!prefs.get<Boolean>(R.string.key_disable_exit_prompt) && lastBackPressTime < System.currentTimeMillis() - 4000) {
                lastBackPressTime = System.currentTimeMillis()
                Util.toast(this, R.string.main_back_confirm)
            } else {
                finish()
            }
        }
    }

    override fun onBackPressedSupport(): Boolean {
        return if (slideUpPanel?.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            removeCurrent()
            false
        } else {
            super.onBackPressedSupport()
        }
    }

    override var currentFragment: SubsonicFragment?
        get() = if (slideUpPanel?.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            if (secondaryFragment == null) {
                nowPlayingFragment
            } else {
                secondaryFragment
            }
        } else {
            super.currentFragment
        }
        set(currentFragment) {
            super.currentFragment = currentFragment
        }

    override fun replaceFragment(fragment: SubsonicFragment?, tag: Int, replaceCurrent: Boolean) {
        if (slideUpPanel != null && slideUpPanel!!.panelState == SlidingUpPanelLayout.PanelState.EXPANDED && !isPanelClosing) {
            secondaryFragment = fragment
            nowPlayingFragment!!.setPrimaryFragment(false)
            secondaryFragment!!.setPrimaryFragment(true)
            supportInvalidateOptionsMenu()
            val trans = supportFragmentManager.beginTransaction()
            trans.setCustomAnimations(
                R.anim.enter_from_right,
                R.anim.exit_to_left,
                R.anim.enter_from_left,
                R.anim.exit_to_right
            )
            trans.hide(nowPlayingFragment!!)
            trans.add(R.id.now_playing_fragment_container, secondaryFragment!!, tag.toString() + "")
            trans.commit()
        } else {
            super.replaceFragment(fragment, tag, replaceCurrent)
        }
    }

    override fun removeCurrent() {
        if (slideUpPanel!!.panelState == SlidingUpPanelLayout.PanelState.EXPANDED && secondaryFragment != null) {
            val trans = supportFragmentManager.beginTransaction()
            trans.setCustomAnimations(
                R.anim.enter_from_left,
                R.anim.exit_to_right,
                R.anim.enter_from_right,
                R.anim.exit_to_left
            )
            trans.remove(secondaryFragment!!)
            trans.show(nowPlayingFragment!!)
            trans.commit()
            secondaryFragment = null
            nowPlayingFragment!!.setPrimaryFragment(true)
            supportInvalidateOptionsMenu()
        } else {
            super.removeCurrent()
        }
    }

    override fun setTitle(title: CharSequence) {
        if (slideUpPanel!!.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            supportActionBar!!.title = title
        } else {
            super.setTitle(title)
        }
    }

    override fun drawerItemSelected(fragmentType: String) {
        super.drawerItemSelected(fragmentType)
        if (slideUpPanel!!.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            slideUpPanel!!.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        }
    }

    override fun startFragmentActivity(fragmentType: String) {
        // Create a transaction that does all of this
        val trans = supportFragmentManager.beginTransaction()

        // Clear existing stack
        for (i in backStack.indices.reversed()) {
            trans.remove(backStack[i]!!)
        }
        trans.remove(currentFragment!!)
        backStack.clear()

        // Create new stack
        currentFragment = getNewFragment(fragmentType)
        currentFragment!!.setPrimaryFragment(true)
        trans.add(
            R.id.fragment_container,
            currentFragment!!,
            currentFragment!!.supportTag.toString() + ""
        )

        // Done, cleanup
        trans.commit()
        supportInvalidateOptionsMenu()
        if (drawer != null) {
            drawer!!.closeDrawers()
        }
        if (secondaryContainer != null) {
            secondaryContainer!!.visibility = View.GONE
        }
        if (drawerToggle != null) {
            drawerToggle!!.isDrawerIndicatorEnabled = true
        }
    }

    override fun openNowPlaying() {
        slideUpPanel!!.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
    }

    override fun closeNowPlaying() {
        slideUpPanel!!.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        isPanelClosing = true
    }

    private fun getNewFragment(fragmentType: String?): SubsonicFragment {
        return if ("Artist" == fragmentType) {
            SelectArtistFragment()
        } else if ("Playlist" == fragmentType) {
            SelectPlaylistFragment()
        } else if ("Podcast" == fragmentType) {
            SelectPodcastsFragment()
        } else if ("Bookmark" == fragmentType) {
            SelectBookmarkFragment()
        } else if ("Share" == fragmentType) {
            SelectShareFragment()
        } else if ("Download" == fragmentType) {
            DownloadFragment()
        } else {
            MainFragment()
        }
    }

    fun checkUpdates() {
        try {
            val version = packageManager.getPackageInfo(packageName, 0).versionName
            val ver = version.replace(".", "").toInt()
            val updater = Updater(ver)
            updater.checkUpdates(this)
        } catch (e: Exception) {
        }
    }

    private fun loadSession() {
        try {
            KeyStoreUtil.loadKeyStore()
        } catch (e: Exception) {
            Log.w(TAG, "Error loading keystore")
            Log.w(TAG, Log.getStackTraceString(e))
        }
        loadSettings()
        if (!Util.isOffline(this) && ServerInfo.canBookmark(this)) {
            loadBookmarks()
        }
        // If we are on Subsonic 5.2+, save play queue
        if (ServerInfo.canSavePlayQueue(this) && !Util.isOffline(this)) {
            loadRemotePlayQueue()
        }
        sessionInitialized = true
    }

    private fun loadSettings() {
        PreferenceManager.setDefaultValues(this, R.xml.settings_appearance, false)
        PreferenceManager.setDefaultValues(this, R.xml.settings_cache, false)
        PreferenceManager.setDefaultValues(this, R.xml.settings_drawer, false)
        PreferenceManager.setDefaultValues(this, R.xml.settings_sync, false)
        PreferenceManager.setDefaultValues(this, R.xml.settings_playback, false)
        val prefs = Util.getPreferences(this)
        if (!prefs.contains(Constants.PREFERENCES_KEY_CACHE_LOCATION) || prefs.getString(
                Constants.PREFERENCES_KEY_CACHE_LOCATION,
                null
            ) == null
        ) {
            resetCacheLocation(prefs)
        } else {
            val path = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null)
            val cacheLocation = File(path)
            if (!FileUtil.verifyCanWrite(cacheLocation)) {
                // Only warn user if there is a difference saved
                if (resetCacheLocation(prefs)) {
                    Util.info(this, R.string.common_warning, R.string.settings_cache_location_reset)
                }
            }
        }
        if (!prefs.contains(Constants.PREFERENCES_KEY_OFFLINE)) {
            var editor = prefs.edit()
            editor.putBoolean(Constants.PREFERENCES_KEY_OFFLINE, false)
            editor.putString(Constants.PREFERENCES_KEY_SERVER_NAME + 1, "Demo Server")
            editor.putString(Constants.PREFERENCES_KEY_SERVER_URL + 1, "http://demo.subsonic.org")
            editor.putString(Constants.PREFERENCES_KEY_USERNAME + 1, "guest")
            if (Build.VERSION.SDK_INT < 23) {
                editor.putString(Constants.PREFERENCES_KEY_PASSWORD + 1, "guest")
            } else {
                // Attempt to encrypt password
                val encryptedDefaultPassword = KeyStoreUtil.encrypt("guest")
                if (encryptedDefaultPassword != null) {
                    // If encryption succeeds, store encrypted password and flag password as encrypted
                    editor.putString(
                        Constants.PREFERENCES_KEY_PASSWORD + 1,
                        encryptedDefaultPassword
                    )
                    editor.putBoolean(Constants.PREFERENCES_KEY_ENCRYPTED_PASSWORD + 1, true)
                } else {
                    // Fall back to plaintext if Keystore is having issue
                    editor = editor.putString(Constants.PREFERENCES_KEY_PASSWORD + 1, "guest")
                    editor.putBoolean(Constants.PREFERENCES_KEY_ENCRYPTED_PASSWORD + 1, false)
                }
            }
            editor.putInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1)
            editor.commit()
        }
        if (!prefs.contains(Constants.PREFERENCES_KEY_SERVER_COUNT)) {
            val editor = prefs.edit()
            editor.putInt(Constants.PREFERENCES_KEY_SERVER_COUNT, 1)
            editor.commit()
        }
    }

    private fun resetCacheLocation(prefs: SharedPreferences): Boolean {
        val newDirectory = FileUtil.getDefaultMusicDirectory(this).path
        val oldDirectory = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null)
        return if (newDirectory == null || oldDirectory != null && newDirectory == oldDirectory) {
            false
        } else {
            val editor = prefs.edit()
            editor.putString(
                Constants.PREFERENCES_KEY_CACHE_LOCATION,
                newDirectory
            )
            editor.commit()
            true
        }
    }

    private fun loadBookmarks() {
        val context: Context = this
        object : SilentBackgroundTask<Void?>(context) {
            @Throws(Throwable::class)
            public override fun doInBackground(): Void? {
                val musicService = MusicServiceFactory.getMusicService(context)
                musicService.getBookmarks(true, context, null)
                return null
            }

            public override fun error(error: Throwable) {
                Log.e(TAG, "Failed to get bookmarks", error)
            }
        }.execute()
    }

    private fun loadRemotePlayQueue() {
        if (Util.getPreferences(this)
                .getBoolean(Constants.PREFERENCES_KEY_RESUME_PLAY_QUEUE_NEVER, false)
        ) {
            return
        }
        val context: SubsonicActivity = this
        object : SilentBackgroundTask<Void?>(this) {
            private var playerQueue: PlayerQueue? = null
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                try {
                    val musicService = MusicServiceFactory.getMusicService(context)
                    val remoteState = musicService.getPlayQueue(context, null)

                    // Make sure we wait until download service is ready
                    var dlService = downloadService
                    while (dlService == null || !dlService.isInitialized) {
                        Util.sleepQuietly(100L)
                        dlService = downloadService
                    }

                    // If we had a remote state and it's changed is more recent than our existing state
                    if (remoteState?.changed != null) {
                        // Check if changed + 30 seconds since some servers have slight skew
                        val remoteChange = Date(remoteState.changed.time - ALLOWED_SKEW)
                        val localChange = dlService.lastStateChanged
                        if (localChange == null || localChange.before(remoteChange)) {
                            playerQueue = remoteState
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get playing queue to server", e)
                }
                return null
            }

            protected override fun done(arg: Void?) {
                if (!context.isDestroyedCompat && playerQueue != null) {
                    promptRestoreFromRemoteQueue(playerQueue!!)
                }
            }
        }.execute()
    }

    private fun promptRestoreFromRemoteQueue(remoteState: PlayerQueue) {
        val builder = AlertDialog.Builder(this)
        val message = resources.getString(
            R.string.common_confirm_message,
            resources.getString(R.string.download_restore_play_queue).lowercase(
                Locale.getDefault()
            ),
            Util.formatDate(remoteState.changed)
        )
        builder.setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.common_confirm)
            .setMessage(message)
            .setPositiveButton(R.string.common_ok) { dialogInterface, i ->
                object : SilentBackgroundTask<Void?>(this@SubsonicFragmentActivity) {
                    @Throws(Throwable::class)
                    override fun doInBackground(): Void? {
                        val downloadService = downloadService
                        downloadService!!.clear()
                        downloadService.download(
                            remoteState.songs,
                            false,
                            false,
                            false,
                            false,
                            remoteState.currentPlayingIndex,
                            remoteState.currentPlayingPosition
                        )
                        return null
                    }
                }.execute()
            }
            .setNeutralButton(R.string.common_cancel) { dialogInterface, i ->
                object : SilentBackgroundTask<Void?>(this@SubsonicFragmentActivity) {
                    @Throws(Throwable::class)
                    override fun doInBackground(): Void? {
                        val downloadService = downloadService
                        downloadService!!.serializeQueue(false)
                        return null
                    }
                }.execute()
            }
            .setNegativeButton(R.string.common_never) { dialogInterface, i ->
                object : SilentBackgroundTask<Void?>(this@SubsonicFragmentActivity) {
                    @Throws(Throwable::class)
                    override fun doInBackground(): Void? {
                        val downloadService = downloadService
                        downloadService!!.serializeQueue(false)
                        val editor = Util.getPreferences(this@SubsonicFragmentActivity).edit()
                        editor.putBoolean(Constants.PREFERENCES_KEY_RESUME_PLAY_QUEUE_NEVER, true)
                        editor.commit()
                        return null
                    }
                }.execute()
            }
        builder.create().show()
    }

    private fun createAccount() {
        val context: Context = this
        object : SilentBackgroundTask<Void?>(this) {
            @Throws(Throwable::class)
            override fun doInBackground(): Void? {
                val accountManager =
                    context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
                val account = Account(Constants.SYNC_ACCOUNT_NAME, Constants.SYNC_ACCOUNT_TYPE)
                accountManager.addAccountExplicitly(account, null, null)
                val prefs = Util.getPreferences(context)
                val syncEnabled = prefs.getBoolean(Constants.PREFERENCES_KEY_SYNC_ENABLED, true)
                val syncInterval = prefs.getString(Constants.PREFERENCES_KEY_SYNC_INTERVAL, "60")!!
                    .toInt()

                // Add enabled/frequency to playlist/podcasts syncing
                ContentResolver.setSyncAutomatically(
                    account,
                    Constants.SYNC_ACCOUNT_PLAYLIST_AUTHORITY,
                    syncEnabled
                )
                ContentResolver.addPeriodicSync(
                    account,
                    Constants.SYNC_ACCOUNT_PLAYLIST_AUTHORITY,
                    Bundle(),
                    60L * syncInterval
                )
                ContentResolver.setSyncAutomatically(
                    account,
                    Constants.SYNC_ACCOUNT_PODCAST_AUTHORITY,
                    syncEnabled
                )
                ContentResolver.addPeriodicSync(
                    account,
                    Constants.SYNC_ACCOUNT_PODCAST_AUTHORITY,
                    Bundle(),
                    60L * syncInterval
                )

                // Add for starred/recently added
                ContentResolver.setSyncAutomatically(
                    account,
                    Constants.SYNC_ACCOUNT_STARRED_AUTHORITY,
                    syncEnabled && prefs.getBoolean(
                        Constants.PREFERENCES_KEY_SYNC_STARRED, false
                    )
                )
                ContentResolver.addPeriodicSync(
                    account,
                    Constants.SYNC_ACCOUNT_STARRED_AUTHORITY,
                    Bundle(),
                    60L * syncInterval
                )
                ContentResolver.setSyncAutomatically(
                    account,
                    Constants.SYNC_ACCOUNT_MOST_RECENT_AUTHORITY,
                    syncEnabled && prefs.getBoolean(
                        Constants.PREFERENCES_KEY_SYNC_MOST_RECENT, false
                    )
                )
                ContentResolver.addPeriodicSync(
                    account,
                    Constants.SYNC_ACCOUNT_MOST_RECENT_AUTHORITY,
                    Bundle(),
                    60L * syncInterval
                )
                return null
            }

            protected override fun done(result: Void?) {}
        }.execute()
    }

    private fun showInfoDialog() {
        if (!infoDialogDisplayed) {
            infoDialogDisplayed = true
            if (Util.getRestUrl(this, null).contains("demo.subsonic.org")) {
                Util.info(this, R.string.main_welcome_title, R.string.main_welcome_text)
            }
        }
    }

    val activeToolbar: Toolbar?
        get() = if (slideUpPanel!!.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) nowPlayingToolbar else mainToolbar

    override fun onSongChanged(
        currentPlaying: DownloadFile?,
        currentPlayingIndex: Int,
        shouldFastForward: Boolean
    ) {
        this.currentPlaying = currentPlaying
        var song: MusicDirectory.Entry? = null
        if (currentPlaying != null) {
            song = currentPlaying.song
            trackView!!.text = song.title
            if (song.artist != null) {
                artistView!!.visibility = View.VISIBLE
                artistView!!.text = song.artist
            } else {
                artistView!!.visibility = View.GONE
            }
        } else {
            trackView!!.setText(R.string.main_title)
            artistView!!.setText(R.string.main_artist)
        }
        if (coverArtView != null) {
            var height = coverArtView!!.height
            if (height <= 0) {
                val attrs = intArrayOf(R.attr.actionBarSize)
                val typedArray = this.obtainStyledAttributes(attrs)
                height = typedArray.getDimensionPixelSize(0, 0)
                typedArray.recycle()
            }
            imageLoader?.loadImage(coverArtView, song, false, height, false)
        }
        updateMediaButtons(shouldFastForward)
    }

    private fun updateMediaButtons(shouldFastForward: Boolean) {
        val downloadService = downloadService
        if (downloadService!!.isCurrentPlayingSingle) {
            previousButton!!.visibility = View.GONE
            nextButton!!.visibility = View.GONE
            rewindButton!!.visibility = View.GONE
            fastforwardButton!!.visibility = View.GONE
        } else {
            if (shouldFastForward) {
                previousButton!!.visibility = View.GONE
                nextButton!!.visibility = View.GONE
                rewindButton!!.visibility = View.VISIBLE
                fastforwardButton!!.visibility = View.VISIBLE
            } else {
                previousButton!!.visibility = View.VISIBLE
                nextButton!!.visibility = View.VISIBLE
                rewindButton!!.visibility = View.GONE
                fastforwardButton!!.visibility = View.GONE
            }
        }
    }

    override fun onSongsChanged(
        songs: List<DownloadFile>?,
        currentPlaying: DownloadFile?,
        currentPlayingIndex: Int,
        shouldFastForward: Boolean
    ) {
        if (this.currentPlaying !== currentPlaying || this.currentPlaying == null) {
            onSongChanged(currentPlaying, currentPlayingIndex, shouldFastForward)
        } else {
            updateMediaButtons(shouldFastForward)
        }
    }

    override fun onSongProgress(
        currentPlaying: DownloadFile?,
        millisPlayed: Int,
        duration: Int,
        isSeekable: Boolean
    ) {
    }

    override fun onStateUpdate(downloadFile: DownloadFile?, playerState: PlayerState) {
        val attrs =
            intArrayOf(if (playerState == PlayerState.STARTED) R.attr.actionbar_pause else R.attr.actionbar_start)
        val typedArray = this.obtainStyledAttributes(attrs)
        startButton!!.setImageResource(typedArray.getResourceId(0, 0))
        typedArray.recycle()
    }

    override fun onMetadataUpdate(song: MusicDirectory.Entry?, fieldChange: Int) {
        if (song != null && coverArtView != null && fieldChange == DownloadService.METADATA_UPDATED_COVER_ART) {
            var height = coverArtView!!.height
            if (height <= 0) {
                val attrs = intArrayOf(R.attr.actionBarSize)
                val typedArray = this.obtainStyledAttributes(attrs)
                height = typedArray.getDimensionPixelSize(0, 0)
                typedArray.recycle()
            }
            imageLoader?.loadImage(coverArtView, song, false, height, false)

            // We need to update it immediately since it won't update if updater is not running for it
            if (nowPlayingFragment != null && slideUpPanel!!.panelState == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                nowPlayingFragment!!.onMetadataUpdate(song, fieldChange)
            }
        }
    }

    companion object {
        private val TAG = SubsonicFragmentActivity::class.java.simpleName
        private var infoDialogDisplayed = false
        private var sessionInitialized = false
        private const val ALLOWED_SKEW = 30000L
    }
}