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

import android.Manifest
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import github.daneren2005.dsub.Preferences
import github.daneren2005.dsub.R
import github.daneren2005.dsub.domain.ServerInfo
import github.daneren2005.dsub.fragments.SubsonicFragment
import github.daneren2005.dsub.service.DownloadService
import github.daneren2005.dsub.service.MusicServiceFactory
import github.daneren2005.dsub.util.Constants
import github.daneren2005.dsub.util.DrawableTint
import github.daneren2005.dsub.util.ImageLoader
import github.daneren2005.dsub.util.SilentBackgroundTask
import github.daneren2005.dsub.util.ThemeUtil
import github.daneren2005.dsub.util.UserUtil
import github.daneren2005.dsub.util.Util
import github.daneren2005.dsub.view.UpdateView
import org.koin.android.ext.android.inject

open class SubsonicActivity : AppCompatActivity() {
    protected val prefs: Preferences.App by inject()

    private val afterServiceAvailable: MutableList<Runnable> = ArrayList()
    private var drawerIdle = true
    var isDestroyedCompat = false
        private set
    private var finished = false
    protected var backStack: MutableList<SubsonicFragment?> = ArrayList()
    open var currentFragment: SubsonicFragment? = null
        protected set
    protected var primaryContainer: View? = null
    protected var secondaryContainer: View? = null
    var isTv = false
        protected set
    var isTouchscreen = true
        protected set
    protected var handler = Handler()
    var rootView: ViewGroup? = null
    var drawer: DrawerLayout? = null
    var drawerToggle: ActionBarDrawerToggle? = null
    var drawerList: NavigationView? = null
    var drawerHeader: View? = null
    var drawerHeaderToggle: ImageView? = null
    var drawerServerName: TextView? = null
    var drawerUserName: TextView? = null
    var lastSelectedPosition = 0
    var showingTabs = true
    var drawerOpen = false
    var preferencesListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    override fun onCreate(bundle: Bundle?) {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            // tv = true;
        }
        val pm = packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            isTouchscreen = false
        }
        applyTheme()
        applyFullscreen()
        super.onCreate(bundle)
        DownloadService.startService(this)
        volumeControlStream = AudioManager.STREAM_MUSIC
        if (intent.hasExtra(Constants.FRAGMENT_POSITION)) {
            lastSelectedPosition = intent.getIntExtra(Constants.FRAGMENT_POSITION, 0)
        }
        if (preferencesListener == null) {
            preferencesListener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key -> // When changing drawer settings change visibility
                    when (key) {
                        Constants.PREFERENCES_KEY_PODCASTS_ENABLED -> setDrawerItemVisible(
                            R.id.drawer_podcasts,
                            false
                        )

                        Constants.PREFERENCES_KEY_BOOKMARKS_ENABLED -> setDrawerItemVisible(
                            R.id.drawer_bookmarks,
                            false
                        )

                        Constants.PREFERENCES_KEY_SHARED_ENABLED -> setDrawerItemVisible(
                            R.id.drawer_shares,
                            false
                        )
                    }
                }
            Util.getPreferences(this).registerOnSharedPreferenceChangeListener(preferencesListener)
        }

        // TODO this doesn't work on Android 12
        val prefs = Util.getPreferences(this)
        val instance = prefs.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1)
        val expectedSSID =
            prefs.getString(Constants.PREFERENCES_KEY_SERVER_LOCAL_NETWORK_SSID + instance, "")
        if (!expectedSSID!!.isEmpty()) {
            val currentSSID = Util.getSSID(this)
            if ("<unknown ssid>" == currentSSID && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSIONS_REQUEST_LOCATION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSIONS_REQUEST_LOCATION -> {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Util.toast(this, R.string.permission_location_failed)
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle?.syncState()
    }
    override fun onStart() {
        super.onStart()
        Util.registerMediaButtonEventReceiver(this)

        // Make sure to update theme
        val prefs = Util.getPreferences(this)
        if (themeName != null && themeName != ThemeUtil.getTheme(this) || fullScreen != prefs.getBoolean(
                getString(R.string.key_fullscreen), false
            ) || actionbarColored != prefs.getBoolean(
                Constants.PREFERENCES_KEY_COLOR_ACTION_BAR, true
            )
        ) {
            restart()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            DrawableTint.clearCache()
            return
        }
        imageLoader!!.onUIVisible()
        UpdateView.addActiveActivity()
    }

    override fun onResume() {
        super.onResume()

        // If this is in onStart is causes crashes when rotating screen in offline mode
        // Actual root cause of error is `drawerItemSelected(newFragment);` in the offline mode branch of code
        populateTabs()
    }

    override fun onStop() {
        super.onStop()
        UpdateView.removeActiveActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroyedCompat = true
        Util.getPreferences(this).unregisterOnSharedPreferenceChangeListener(preferencesListener)
    }

    override fun finish() {
        super.finish()
        Util.disablePendingTransition(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setContentView(viewId: Int) {
        if (isTv) {
            super.setContentView(R.layout.static_drawer_activity)
        } else if (Util.getPreferences(this)
                .getBoolean(getString(R.string.key_fullscreen), false)
        ) {
            super.setContentView(R.layout.abstract_fullscreen_activity)
        } else {
            super.setContentView(R.layout.abstract_activity)
        }
        rootView = findViewById<View>(R.id.content_frame) as ViewGroup
        if (viewId != 0) {
            val layoutInflater = layoutInflater
            layoutInflater.inflate(viewId, rootView)
        }
        drawerList = findViewById<View>(R.id.left_drawer) as NavigationView
        drawerList!!.setNavigationItemSelectedListener(NavigationView.OnNavigationItemSelectedListener { menuItem ->
            if (showingTabs) {
                // Settings are on a different selectable track
                if (menuItem.itemId != R.id.drawer_settings && menuItem.itemId != R.id.drawer_offline) {
                    menuItem.isChecked = true
                    lastSelectedPosition = menuItem.itemId
                }
                when (menuItem.itemId) {
                    R.id.drawer_home -> {
                        drawerItemSelected("Home")
                        return@OnNavigationItemSelectedListener true
                    }

                    R.id.drawer_library -> {
                        drawerItemSelected("Artist")
                        return@OnNavigationItemSelectedListener true
                    }

                    R.id.drawer_playlists -> {
                        drawerItemSelected("Playlist")
                        return@OnNavigationItemSelectedListener true
                    }

                    R.id.drawer_podcasts -> {
                        drawerItemSelected("Podcast")
                        return@OnNavigationItemSelectedListener true
                    }

                    R.id.drawer_bookmarks -> {
                        drawerItemSelected("Bookmark")
                        return@OnNavigationItemSelectedListener true
                    }

                    R.id.drawer_shares -> {
                        drawerItemSelected("Share")
                        return@OnNavigationItemSelectedListener true
                    }

                    R.id.drawer_downloading -> {
                        drawerItemSelected("Download")
                        return@OnNavigationItemSelectedListener true
                    }

                    R.id.drawer_offline -> {
                        toggleOffline()
                        return@OnNavigationItemSelectedListener true
                    }

                    R.id.drawer_settings -> {
                        startActivity(Intent(this@SubsonicActivity, SettingsActivity::class.java))
                        drawer!!.closeDrawers()
                        return@OnNavigationItemSelectedListener true
                    }
                }
            } else {
                val activeServer = menuItem.itemId - MENU_ITEM_SERVER_BASE
                setActiveServer(activeServer)
                populateTabs()
                return@OnNavigationItemSelectedListener true
            }
            false
        })
        drawerHeader = drawerList!!.inflateHeaderView(R.layout.drawer_header).apply {
            setOnClickListener(View.OnClickListener {
                if (showingTabs) {
                    populateServers()
                } else {
                    populateTabs()
                }
            })
            drawerHeaderToggle = findViewById<View>(R.id.header_select_image) as ImageView
            drawerServerName = findViewById<View>(R.id.header_server_name) as TextView
            drawerUserName = findViewById<View>(R.id.header_user_name) as TextView
        }

        updateDrawerHeader()
        if (!isTv) {
            drawer = findViewById<View>(R.id.drawer_layout) as DrawerLayout

            // Pass in toolbar if it exists
            val toolbar = findViewById<View>(R.id.main_toolbar) as Toolbar
            drawerToggle = object : ActionBarDrawerToggle(
                this,
                drawer,
                toolbar,
                R.string.common_appname,
                R.string.common_appname
            ) {
                override fun onDrawerClosed(view: View) {
                    drawerIdle = true
                    drawerOpen = false
                    if (!showingTabs) {
                        populateTabs()
                    }
                }

                override fun onDrawerOpened(view: View) {
                    var downloadingVisible = downloadService?.backgroundDownloads?.isNotEmpty() ?: false
                    if (lastSelectedPosition == R.id.drawer_downloading) {
                        downloadingVisible = true
                    }
                    setDrawerItemVisible(R.id.drawer_downloading, downloadingVisible)
                    drawerIdle = true
                    drawerOpen = true
                }

                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    super.onDrawerSlide(drawerView, slideOffset)
                    drawerIdle = false
                }
            }
            drawer!!.setDrawerListener(drawerToggle)
            drawerToggle!!.isDrawerIndicatorEnabled = true
            drawer!!.setOnTouchListener { v, event ->
                if (drawerIdle) {
                    currentFragment?.gestureDetector?.onTouchEvent(event) ?: false
                } else {
                    false
                }
            }
        }

        // Check whether this is a tablet or not
        secondaryContainer = findViewById(R.id.fragment_second_container)
        if (secondaryContainer != null) {
            primaryContainer = findViewById(R.id.fragment_container)
        }
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        val ids = arrayOfNulls<String>(backStack.size + 1)
        ids[0] = currentFragment!!.tag
        var i = 1
        for (frag in backStack) {
            ids[i] = frag!!.tag
            i++
        }
        savedInstanceState.putStringArray(Constants.MAIN_BACK_STACK, ids)
        savedInstanceState.putInt(Constants.MAIN_BACK_STACK_SIZE, backStack.size + 1)
        savedInstanceState.putInt(Constants.FRAGMENT_POSITION, lastSelectedPosition)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val size = savedInstanceState.getInt(Constants.MAIN_BACK_STACK_SIZE)
        val ids = savedInstanceState.getStringArray(Constants.MAIN_BACK_STACK)
        val fm = supportFragmentManager
        currentFragment = fm.findFragmentByTag(ids!![0]) as SubsonicFragment?
        currentFragment!!.setPrimaryFragment(true)
        currentFragment!!.setSupportTag(ids[0])
        supportInvalidateOptionsMenu()
        var trans = supportFragmentManager.beginTransaction()
        for (i in 1 until size) {
            val frag = fm.findFragmentByTag(ids[i]) as SubsonicFragment?
            frag!!.setSupportTag(ids[i])
            if (secondaryContainer != null) {
                frag.setPrimaryFragment(false, true)
            }
            trans.hide(frag)
            backStack.add(frag)
        }
        trans.commit()

        // Current fragment is hidden in secondaryContainer
        if (secondaryContainer == null && !currentFragment!!.isVisible) {
            trans = supportFragmentManager.beginTransaction()
            trans.remove(currentFragment!!)
            trans.commit()
            supportFragmentManager.executePendingTransactions()
            trans = supportFragmentManager.beginTransaction()
            trans.add(R.id.fragment_container, currentFragment!!, ids[0])
            trans.commit()
        } else if (secondaryContainer != null && secondaryContainer!!.findViewById<View?>(
                currentFragment!!.rootId
            ) == null && backStack.size > 0
        ) {
            trans = supportFragmentManager.beginTransaction()
            trans.remove(currentFragment!!)
            trans.show(backStack[backStack.size - 1]!!)
            trans.commit()
            supportFragmentManager.executePendingTransactions()
            trans = supportFragmentManager.beginTransaction()
            trans.add(R.id.fragment_second_container, currentFragment!!, ids[0])
            trans.commit()
            secondaryContainer!!.visibility = View.VISIBLE
        }
        lastSelectedPosition = savedInstanceState.getInt(Constants.FRAGMENT_POSITION)
        if (lastSelectedPosition != 0) {
            val item = drawerList!!.menu.findItem(lastSelectedPosition)
            if (item != null) {
                item.isChecked = true
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val menuInflater = menuInflater
        val currentFragment = currentFragment
        if (currentFragment != null) {
            try {
                val fragment = this.currentFragment
                fragment!!.setContext(this)
                fragment.onCreateOptionsMenu(menu, menuInflater)
                if (isTouchscreen) {
                    menu.setGroupVisible(R.id.not_touchscreen, false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error on creating options menu", e)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle != null && drawerToggle!!.onOptionsItemSelected(item)) {
            return true
        } else if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return currentFragment!!.onOptionsItemSelected(item)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolumeAdjust = isVolumeDown || isVolumeUp
        val isJukebox = downloadService != null && downloadService!!.isRemoteEnabled
        if (isVolumeAdjust && isJukebox) {
            downloadService!!.updateRemoteVolume(isVolumeUp)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun setTitle(title: CharSequence) {
        if (title != supportActionBar!!.title) {
            supportActionBar?.title = title
        }
    }

    fun setSubtitle(title: CharSequence?) {
        supportActionBar!!.subtitle = title
    }

    private fun populateTabs() {
        drawerList!!.menu.clear()
        drawerList!!.inflateMenu(R.menu.drawer_navigation)
        val prefs = Util.getPreferences(this)
        val podcastsEnabled = prefs.getBoolean(Constants.PREFERENCES_KEY_PODCASTS_ENABLED, true)
        val bookmarksEnabled =
            prefs.getBoolean(Constants.PREFERENCES_KEY_BOOKMARKS_ENABLED, true) && !Util.isOffline(
                this
            ) && ServerInfo.canBookmark(this)
        val sharedEnabled = prefs.getBoolean(
            Constants.PREFERENCES_KEY_SHARED_ENABLED,
            true
        ) && !Util.isOffline(this)
        val offlineMenuItem = drawerList!!.menu.findItem(R.id.drawer_offline)
        if (Util.isOffline(this)) {
            setDrawerItemVisible(R.id.drawer_home, false)
            if (lastSelectedPosition == 0 || lastSelectedPosition == R.id.drawer_home) {
                var newFragment = Util.openToTab(this)
                if (newFragment == null || "Home" == newFragment) {
                    newFragment = "Artist"
                }
                lastSelectedPosition = getDrawerItemId(newFragment)
                drawerItemSelected(newFragment)
            }
            offlineMenuItem.setTitle(R.string.main_online)
        } else {
            offlineMenuItem.setTitle(R.string.main_offline)
        }
        if (!podcastsEnabled) {
            setDrawerItemVisible(R.id.drawer_podcasts, false)
        }
        if (!bookmarksEnabled) {
            setDrawerItemVisible(R.id.drawer_bookmarks, false)
        }
        if (!sharedEnabled) {
            setDrawerItemVisible(R.id.drawer_shares, false)
        }
        if (lastSelectedPosition != 0) {
            val item = drawerList!!.menu.findItem(lastSelectedPosition)
            if (item != null) {
                item.isChecked = true
            }
        }
        drawerHeaderToggle!!.setImageResource(R.drawable.main_select_server_dark)
        showingTabs = true
    }

    private fun populateServers() {
        drawerList!!.menu.clear()
        val serverCount = Util.getServerCount(this)
        val activeServer = Util.getActiveServer(this)
        for (i in 1..serverCount) {
            val item = drawerList!!.menu.add(
                MENU_GROUP_SERVER,
                MENU_ITEM_SERVER_BASE + i,
                MENU_ITEM_SERVER_BASE + i,
                Util.getServerName(this, i)
            )
            if (activeServer == i) {
                item.isChecked = true
            }
        }
        drawerList!!.menu.setGroupCheckable(MENU_GROUP_SERVER, true, true)
        drawerHeaderToggle!!.setImageResource(R.drawable.main_select_tabs_dark)
        showingTabs = false
    }

    private fun setDrawerItemVisible(id: Int, visible: Boolean) {
        val item = drawerList!!.menu.findItem(id)
        if (item != null) {
            item.isVisible = visible
        }
    }

    protected open fun drawerItemSelected(fragmentType: String) {
        if (currentFragment != null) {
            currentFragment!!.stopActionMode()
        }
        startFragmentActivity(fragmentType)
    }

    open fun startFragmentActivity(fragmentType: String) {
        val intent = Intent()
        intent.setClass(this@SubsonicActivity, SubsonicFragmentActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        if ("" != fragmentType) {
            intent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, fragmentType)
        }
        if (lastSelectedPosition != 0) {
            intent.putExtra(Constants.FRAGMENT_POSITION, lastSelectedPosition)
        }
        startActivity(intent)
        finish()
    }

    protected fun exit() {
        if ((this as Any).javaClass != SubsonicFragmentActivity::class.java) {
            val intent = Intent(this, SubsonicFragmentActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            intent.putExtra(Constants.INTENT_EXTRA_NAME_EXIT, true)
            Util.startActivityWithoutTransition(this, intent)
        } else {
            finished = true
            stopService(Intent(this, DownloadService::class.java))
            finish()
        }
    }

    open fun onBackPressedSupport(): Boolean {
        return if (drawerOpen) {
            drawer!!.closeDrawers()
            false
        } else if (backStack.size > 0) {
            removeCurrent()
            false
        } else {
            true
        }
    }

    override fun onBackPressed() {
        if (onBackPressedSupport()) {
            super.onBackPressed()
        }
    }

    fun replaceFragment(fragment: SubsonicFragment?, tag: Int) {
        replaceFragment(fragment, tag, false)
    }

    open fun replaceFragment(fragment: SubsonicFragment?, tag: Int, replaceCurrent: Boolean) {
        val oldFragment = currentFragment
        if (currentFragment != null) {
            currentFragment!!.setPrimaryFragment(false, secondaryContainer != null)
        }
        backStack.add(currentFragment)
        currentFragment = fragment
        currentFragment!!.setPrimaryFragment(true)
        supportInvalidateOptionsMenu()
        if (secondaryContainer == null || oldFragment!!.isAlwaysFullscreen || currentFragment!!.isAlwaysStartFullscreen) {
            val trans = supportFragmentManager.beginTransaction()
            trans.setCustomAnimations(
                R.anim.enter_from_right,
                R.anim.exit_to_left,
                R.anim.enter_from_left,
                R.anim.exit_to_right
            )
            trans.hide(oldFragment!!)
            trans.add(R.id.fragment_container, fragment!!, tag.toString() + "")
            trans.commit()
        } else {
            // Make sure secondary container is visible now
            secondaryContainer!!.visibility = View.VISIBLE
            var trans = supportFragmentManager.beginTransaction()

            // Check to see if you need to put on top of old left or not
            if (backStack.size > 1) {
                // Move old right to left if there is a backstack already
                val newLeftFragment = backStack[backStack.size - 1]
                if (replaceCurrent) {
                    // trans.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
                }
                trans.remove(newLeftFragment!!)

                // Only move right to left if replaceCurrent is false
                if (!replaceCurrent) {
                    val oldLeftFragment = backStack[backStack.size - 2]
                    oldLeftFragment!!.setSecondaryFragment(false)
                    // trans.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
                    trans.hide(oldLeftFragment)

                    // Make sure remove is finished before adding
                    trans.commit()
                    supportFragmentManager.executePendingTransactions()
                    trans = supportFragmentManager.beginTransaction()
                    // trans.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
                    trans.add(
                        R.id.fragment_container,
                        newLeftFragment,
                        newLeftFragment.supportTag.toString() + ""
                    )
                } else {
                    backStack.removeAt(backStack.size - 1)
                }
            }

            // Add fragment to the right container
            trans.setCustomAnimations(
                R.anim.enter_from_right,
                R.anim.exit_to_left,
                R.anim.enter_from_left,
                R.anim.exit_to_right
            )
            trans.add(R.id.fragment_second_container, fragment!!, tag.toString() + "")

            // Commit it all
            trans.commit()
            oldFragment.setIsOnlyVisible(false)
            currentFragment!!.setIsOnlyVisible(false)
        }
    }

    open fun removeCurrent() {
        // Don't try to remove current if there is no backstack to remove from
        if (backStack.isEmpty()) {
            return
        }
        if (currentFragment != null) {
            currentFragment!!.setPrimaryFragment(false)
        }
        val oldFragment = currentFragment
        currentFragment = backStack.removeAt(backStack.size - 1)
        currentFragment!!.setPrimaryFragment(true, false)
        supportInvalidateOptionsMenu()
        if (secondaryContainer == null || currentFragment!!.isAlwaysFullscreen || oldFragment!!.isAlwaysStartFullscreen) {
            val trans = supportFragmentManager.beginTransaction()
            trans.setCustomAnimations(
                R.anim.enter_from_left,
                R.anim.exit_to_right,
                R.anim.enter_from_right,
                R.anim.exit_to_left
            )
            trans.remove(oldFragment!!)
            trans.show(currentFragment!!)
            trans.commit()
        } else {
            var trans = supportFragmentManager.beginTransaction()

            // Remove old right fragment
            trans.setCustomAnimations(
                R.anim.enter_from_left,
                R.anim.exit_to_right,
                R.anim.enter_from_right,
                R.anim.exit_to_left
            )
            trans.remove(oldFragment)

            // Only switch places if there is a backstack, otherwise primary container is correct
            if (backStack.size > 0 && !backStack[backStack.size - 1]!!
                    .isAlwaysFullscreen && !currentFragment!!.isAlwaysStartFullscreen
            ) {
                trans.setCustomAnimations(0, 0, 0, 0)
                // Add current left fragment to right side
                trans.remove(currentFragment!!)

                // Make sure remove is finished before adding
                trans.commit()
                supportFragmentManager.executePendingTransactions()
                trans = supportFragmentManager.beginTransaction()
                // trans.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_left);
                trans.add(
                    R.id.fragment_second_container,
                    currentFragment!!,
                    currentFragment!!.supportTag.toString() + ""
                )
                val newLeftFragment = backStack[backStack.size - 1]
                newLeftFragment!!.setSecondaryFragment(true)
                trans.show(newLeftFragment)
            } else {
                secondaryContainer!!.startAnimation(
                    AnimationUtils.loadAnimation(
                        this,
                        R.anim.exit_to_right
                    )
                )
                secondaryContainer!!.visibility = View.GONE
                currentFragment!!.setIsOnlyVisible(true)
            }
            trans.commit()
        }
    }

    fun replaceExistingFragment(fragment: SubsonicFragment?, tag: Int) {
        val trans = supportFragmentManager.beginTransaction()
        trans.remove(currentFragment!!)
        trans.add(R.id.fragment_container, fragment!!, tag.toString() + "")
        trans.commit()
        currentFragment = fragment
        currentFragment!!.setPrimaryFragment(true)
        supportInvalidateOptionsMenu()
    }

    fun invalidate() {
        if (currentFragment != null) {
            while (backStack.size > 0) {
                removeCurrent()
            }
            currentFragment!!.invalidate()
            populateTabs()
        }
        supportInvalidateOptionsMenu()
    }

    protected fun restart(resumePosition: Boolean = true) {
        val intent = Intent(this, this.javaClass)
        intent.putExtras(getIntent())
        if (resumePosition) {
            intent.putExtra(Constants.FRAGMENT_POSITION, lastSelectedPosition)
        } else {
            val fragmentType = Util.openToTab(this)
            intent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, fragmentType)
            intent.putExtra(Constants.FRAGMENT_POSITION, getDrawerItemId(fragmentType))
        }
        finish()
        Util.startActivityWithoutTransition(this, intent)
    }

    private fun applyTheme() {
        themeName = ThemeUtil.getTheme(this)
        if (themeName != null && themeName!!.indexOf("fullscreen") != -1) {
            themeName = themeName!!.substring(0, themeName!!.indexOf("_fullscreen"))
            ThemeUtil.setTheme(this, themeName!!)
        }
        ThemeUtil.applyTheme(this, themeName!!)
        actionbarColored =
            Util.getPreferences(this).getBoolean(Constants.PREFERENCES_KEY_COLOR_ACTION_BAR, true)
    }

    private fun applyFullscreen() {
        fullScreen =
            Util.getPreferences(this).getBoolean(getString(R.string.key_fullscreen), false)
        if (fullScreen || isTv) {
            // Hide additional elements on higher Android versions
            val flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            window.decorView.systemUiVisibility = flags
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    @get:Synchronized
    val imageLoader: ImageLoader?
        get() {
            if (IMAGE_LOADER == null) {
                IMAGE_LOADER = ImageLoader(this)
            }
            return IMAGE_LOADER
        }
    val downloadService: DownloadService?
        get() {
            if (finished) {
                return null
            }

            // If service is not available, request it to start and wait for it.
            for (i in 0..4) {
                val downloadService = DownloadService.getInstance()
                if (downloadService != null) {
                    break
                }
                Log.w(TAG, "DownloadService not running. Attempting to start it.")
                DownloadService.startService(this)
                Util.sleepQuietly(50L)
            }
            val downloadService = DownloadService.getInstance()
            if (downloadService != null && afterServiceAvailable.size > 0) {
                for (runnable in afterServiceAvailable) {
                    handler.post(runnable)
                }
                afterServiceAvailable.clear()
            }
            return downloadService
        }

    fun runWhenServiceAvailable(runnable: Runnable) {
        if (downloadService != null) {
            runnable.run()
        } else {
            afterServiceAvailable.add(runnable)
            checkIfServiceAvailable()
        }
    }

    private fun checkIfServiceAvailable() {
        if (downloadService == null) {
            handler.postDelayed({ checkIfServiceAvailable() }, 50)
        } else if (afterServiceAvailable.size > 0) {
            for (runnable in afterServiceAvailable) {
                handler.post(runnable)
            }
            afterServiceAvailable.clear()
        }
    }

    open fun openNowPlaying() {}
    open fun closeNowPlaying() {}
    fun setActiveServer(instance: Int) {
        if (Util.getActiveServer(this) != instance) {
            val service = downloadService
            if (service != null) {
                object : SilentBackgroundTask<Void?>(this) {
                    @Throws(Throwable::class)
                    override fun doInBackground(): Void? {
                        service.clearIncomplete()
                        return null
                    }
                }.execute()
            }
            Util.setActiveServer(this, instance)
            invalidate()
            UserUtil.refreshCurrentUser(this, false, true)
            updateDrawerHeader()
        }
    }

    fun updateDrawerHeader() {
        if (Util.isOffline(this)) {
            drawerServerName!!.setText(R.string.select_album_offline)
            drawerUserName!!.text = ""
            drawerHeader!!.isClickable = false
            drawerHeaderToggle!!.visibility = View.GONE
        } else {
            drawerServerName!!.text = Util.getServerName(this)
            drawerUserName!!.text = UserUtil.getCurrentUsername(this)
            drawerHeader!!.isClickable = true
            drawerHeaderToggle!!.visibility = View.VISIBLE
        }
    }

    fun toggleOffline() {
        val isOffline = Util.isOffline(this)
        Util.setOffline(this, !isOffline)
        invalidate()
        val service = downloadService
        service?.setOnline(isOffline)

        // Coming back online
        if (isOffline) {
            val scrobblesCount = Util.offlineScrobblesCount(this)
            val starsCount = Util.offlineStarsCount(this)
            if (scrobblesCount > 0 || starsCount > 0) {
                showOfflineSyncDialog(scrobblesCount, starsCount)
            }
        }
        UserUtil.seedCurrentUser(this)
        updateDrawerHeader()
        drawer!!.closeDrawers()
    }

    private fun showOfflineSyncDialog(scrobbleCount: Int, starsCount: Int) {
        val syncDefault = Util.getSyncDefault(this)
        if (syncDefault != null) {
            if ("sync" == syncDefault) {
                syncOffline(scrobbleCount, starsCount)
                return
            } else if ("delete" == syncDefault) {
                deleteOffline()
                return
            }
        }
        val checkBoxView = this.layoutInflater.inflate(R.layout.sync_dialog, null)
        val checkBox = checkBoxView.findViewById<View>(R.id.sync_default) as CheckBox
        val builder = AlertDialog.Builder(this)
        builder.setIcon(android.R.drawable.ic_dialog_info)
            .setTitle(R.string.offline_sync_dialog_title)
            .setMessage(
                this.resources.getString(
                    R.string.offline_sync_dialog_message,
                    scrobbleCount,
                    starsCount
                )
            )
            .setView(checkBoxView)
            .setPositiveButton(R.string.common_ok) { dialogInterface, i ->
                if (checkBox.isChecked) {
                    Util.setSyncDefault(this@SubsonicActivity, "sync")
                }
                syncOffline(scrobbleCount, starsCount)
            }
            .setNeutralButton(R.string.common_cancel) { dialogInterface, i -> dialogInterface.dismiss() }
            .setNegativeButton(R.string.common_delete) { dialogInterface, i ->
                if (checkBox.isChecked) {
                    Util.setSyncDefault(this@SubsonicActivity, "delete")
                }
                deleteOffline()
            }
        builder.create().show()
    }

    private fun syncOffline(scrobbleCount: Int, starsCount: Int) {
        object : SilentBackgroundTask<Int?>(this) {
            @Throws(Throwable::class)
            override fun doInBackground(): Int {
                val musicService = MusicServiceFactory.getMusicService(this@SubsonicActivity)
                return musicService.processOfflineSyncs(this@SubsonicActivity, null)
            }

            override fun done(result: Int?) {
                if (result == scrobbleCount) {
                    Util.toast(
                        this@SubsonicActivity,
                        resources.getString(R.string.offline_sync_success, result)
                    )
                } else {
                    Util.toast(
                        this@SubsonicActivity,
                        resources.getString(
                            R.string.offline_sync_partial,
                            result,
                            scrobbleCount + starsCount
                        )
                    )
                }
            }

            override fun error(error: Throwable) {
                Log.w(TAG, "Failed to sync offline stats", error)
                val msg =
                    resources.getString(R.string.offline_sync_error) + " " + getErrorMessage(error)
                Util.toast(this@SubsonicActivity, msg)
            }
        }.execute()
    }

    private fun deleteOffline() {
        val offline = Util.getOfflineSync(this).edit()
        offline.putInt(Constants.OFFLINE_SCROBBLE_COUNT, 0)
        offline.putInt(Constants.OFFLINE_STAR_COUNT, 0)
        offline.commit()
    }

    fun getDrawerItemId(fragmentType: String?): Int {
        return if (fragmentType == null) {
            R.id.drawer_home
        } else when (fragmentType) {
            "Home" -> R.id.drawer_home
            "Artist" -> R.id.drawer_library
            "Playlist" -> R.id.drawer_playlists
            "Podcast" -> R.id.drawer_podcasts
            "Bookmark" -> R.id.drawer_bookmarks
            "Share" -> R.id.drawer_shares
            else -> R.id.drawer_home
        }
    }

    companion object {
        private val TAG = SubsonicActivity::class.java.simpleName
        private var IMAGE_LOADER: ImageLoader? = null
        var themeName: String? = null
            protected set
        protected var fullScreen = false
        protected var actionbarColored = false
        private const val MENU_GROUP_SERVER = 10
        private const val MENU_ITEM_SERVER_BASE = 100
        const val PERMISSIONS_REQUEST_LOCATION = 2

        init {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        @JvmStatic
		@Synchronized
        fun getStaticImageLoader(context: Context?): ImageLoader? {
            if (IMAGE_LOADER == null) {
                IMAGE_LOADER = ImageLoader(context)
            }
            return IMAGE_LOADER
        }
    }
}