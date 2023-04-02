/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package github.daneren2005.dsub.util

import android.annotation.TargetApi
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.text.Html
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.util.SparseArray
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import github.daneren2005.dsub.R
import github.daneren2005.dsub.adapter.DetailsAdapter
import github.daneren2005.dsub.domain.MusicDirectory
import github.daneren2005.dsub.domain.PlayerState
import github.daneren2005.dsub.domain.RepeatMode
import github.daneren2005.dsub.domain.ServerInfo
import github.daneren2005.dsub.receiver.MediaButtonIntentReceiver
import github.daneren2005.dsub.service.DownloadService
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.TimeZone

/**
 * @author Sindre Mehus
 * @version $Id$
 */
object Util {
    private val TAG = Util::class.java.simpleName
    private val GIGA_BYTE_FORMAT = DecimalFormat("0.00 GB")
    private val MEGA_BYTE_FORMAT = DecimalFormat("0.00 MB")
    private val KILO_BYTE_FORMAT = DecimalFormat("0 KB")
    private var GIGA_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var MEGA_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var KILO_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private val DATE_FORMAT_SHORT = SimpleDateFormat("MMM d h:mm a")
    private val DATE_FORMAT_LONG = SimpleDateFormat("MMM d, yyyy h:mm a")
    private val DATE_FORMAT_NO_TIME = SimpleDateFormat("MMM d, yyyy")
    private val CURRENT_YEAR = Date().year
    const val EVENT_META_CHANGED = "github.daneren2005.dsub.EVENT_META_CHANGED"
    const val EVENT_PLAYSTATE_CHANGED = "github.daneren2005.dsub.EVENT_PLAYSTATE_CHANGED"
    const val AVRCP_PLAYSTATE_CHANGED = "com.android.music.playstatechanged"
    const val AVRCP_METADATA_CHANGED = "com.android.music.metachanged"
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pauseFocus = false
    private var lowerFocus = false

    // Used by hexEncode()
    private val HEX_DIGITS =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    private var toast: Toast? = null

    // private static Map<Integer, Pair<String, String>> tokens = new HashMap<>();
    private val tokens = SparseArray<Pair<String, String?>>()
    var random: Random? = null
        get() {
            if (field == null) {
                field = SecureRandom()
            }
            return field
        }
        private set

    @JvmStatic
	fun isOffline(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(Constants.PREFERENCES_KEY_OFFLINE, false)
    }

    @JvmStatic
	fun setOffline(context: Context, offline: Boolean) {
        val prefs = getPreferences(context)
        val editor = prefs.edit()
        editor.putBoolean(Constants.PREFERENCES_KEY_OFFLINE, offline)
        editor.commit()
    }

    @JvmStatic
	fun isScreenLitOnDownload(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SCREEN_LIT_ON_DOWNLOAD, false)
    }

    @JvmStatic
	fun getRepeatMode(context: Context): RepeatMode {
        val prefs = getPreferences(context)
        return RepeatMode.valueOf(
            prefs.getString(
                Constants.PREFERENCES_KEY_REPEAT_MODE,
                RepeatMode.OFF.name
            )!!
        )
    }

    @JvmStatic
	fun setRepeatMode(context: Context, repeatMode: RepeatMode) {
        val prefs = getPreferences(context)
        val editor = prefs.edit()
        editor.putString(Constants.PREFERENCES_KEY_REPEAT_MODE, repeatMode.name)
        editor.commit()
    }

    @JvmStatic
	fun isScrobblingEnabled(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(
            Constants.PREFERENCES_KEY_SCROBBLE,
            true
        ) && (isOffline(context) || UserUtil.canScrobble())
    }

    @JvmStatic
	fun setActiveServer(context: Context, instance: Int) {
        val prefs = getPreferences(context)
        val editor = prefs.edit()
        editor.putInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, instance)
        editor.commit()
    }

    @JvmStatic
	fun getActiveServer(context: Context): Int {
        val prefs = getPreferences(context)
        // Don't allow the SERVER_INSTANCE to ever be 0
        return if (prefs.getBoolean(Constants.PREFERENCES_KEY_OFFLINE, false)) 0 else Math.max(
            1, prefs.getInt(
                Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1
            )
        )
    }

    @JvmStatic
	fun getMostRecentActiveServer(context: Context): Int {
        val prefs = getPreferences(context)
        return Math.max(1, prefs.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1))
    }

    @JvmStatic
	fun getServerCount(context: Context): Int {
        val prefs = getPreferences(context)
        return prefs.getInt(Constants.PREFERENCES_KEY_SERVER_COUNT, 1)
    }

    @JvmStatic
	fun removeInstanceName(context: Context, instance: Int, activeInstance: Int) {
        val prefs = getPreferences(context)
        val editor = prefs.edit()
        val newInstance = instance + 1

        // Get what the +1 server details are
        val server = prefs.getString(Constants.PREFERENCES_KEY_SERVER_KEY + newInstance, null)
        val serverName = prefs.getString(Constants.PREFERENCES_KEY_SERVER_NAME + newInstance, null)
        val serverUrl = prefs.getString(Constants.PREFERENCES_KEY_SERVER_URL + newInstance, null)
        val userName = prefs.getString(Constants.PREFERENCES_KEY_USERNAME + newInstance, null)
        var password = prefs.getString(Constants.PREFERENCES_KEY_PASSWORD + newInstance, null)
        if (password != null && prefs.getBoolean(
                Constants.PREFERENCES_KEY_ENCRYPTED_PASSWORD + instance,
                false
            )
        ) password = KeyStoreUtil.decrypt(password)
        val musicFolderId =
            prefs.getString(Constants.PREFERENCES_KEY_MUSIC_FOLDER_ID + newInstance, null)

        // Store the +1 server details in the to be deleted instance
        editor.putString(Constants.PREFERENCES_KEY_SERVER_KEY + instance, server)
        editor.putString(Constants.PREFERENCES_KEY_SERVER_NAME + instance, serverName)
        editor.putString(Constants.PREFERENCES_KEY_SERVER_URL + instance, serverUrl)
        editor.putString(Constants.PREFERENCES_KEY_USERNAME + instance, userName)
        editor.putString(Constants.PREFERENCES_KEY_PASSWORD + instance, password)
        editor.putString(Constants.PREFERENCES_KEY_MUSIC_FOLDER_ID + instance, musicFolderId)

        // Delete the +1 server instance
        // Calling method will loop up to fill this in if +2 server exists
        editor.putString(Constants.PREFERENCES_KEY_SERVER_KEY + newInstance, null)
        editor.putString(Constants.PREFERENCES_KEY_SERVER_NAME + newInstance, null)
        editor.putString(Constants.PREFERENCES_KEY_SERVER_URL + newInstance, null)
        editor.putString(Constants.PREFERENCES_KEY_USERNAME + newInstance, null)
        editor.putString(Constants.PREFERENCES_KEY_PASSWORD + newInstance, null)
        editor.putString(Constants.PREFERENCES_KEY_MUSIC_FOLDER_ID + newInstance, null)
        editor.commit()
        if (instance == activeInstance) {
            if (instance != 1) {
                setActiveServer(context, 1)
            } else {
                setOffline(context, true)
            }
        } else if (newInstance == activeInstance) {
            setActiveServer(context, instance)
        }
    }

    @JvmStatic
	fun getServerName(context: Context): String? {
        val prefs = getPreferences(context)
        val instance = prefs.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1)
        return prefs.getString(Constants.PREFERENCES_KEY_SERVER_NAME + instance, null)
    }

    @JvmStatic
	fun getServerName(context: Context, instance: Int): String? {
        val prefs = getPreferences(context)
        return prefs.getString(Constants.PREFERENCES_KEY_SERVER_NAME + instance, null)
    }

    @JvmStatic
	fun setSelectedMusicFolderId(context: Context, musicFolderId: String?) {
        val instance = getActiveServer(context)
        val prefs = getPreferences(context)
        val editor = prefs.edit()
        editor.putString(Constants.PREFERENCES_KEY_MUSIC_FOLDER_ID + instance, musicFolderId)
        editor.commit()
    }

    @JvmStatic
	fun getSelectedMusicFolderId(context: Context): String? {
        return getSelectedMusicFolderId(context, getActiveServer(context))
    }

    @JvmStatic
	fun getSelectedMusicFolderId(context: Context, instance: Int): String? {
        val prefs = getPreferences(context)
        return prefs.getString(Constants.PREFERENCES_KEY_MUSIC_FOLDER_ID + instance, null)
    }

    @JvmStatic
	fun getAlbumListsPerFolder(context: Context): Boolean {
        return getAlbumListsPerFolder(context, getActiveServer(context))
    }

    @JvmStatic
	fun getAlbumListsPerFolder(context: Context, instance: Int): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(Constants.PREFERENCES_KEY_ALBUMS_PER_FOLDER + instance, false)
    }

    @JvmStatic
	fun setAlbumListsPerFolder(context: Context, perFolder: Boolean) {
        val instance = getActiveServer(context)
        val prefs = getPreferences(context)
        val editor = prefs.edit()
        editor.putBoolean(Constants.PREFERENCES_KEY_ALBUMS_PER_FOLDER + instance, perFolder)
        editor.commit()
    }

    @JvmStatic
	fun getMaxBitrate(context: Context): Int {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = manager.activeNetworkInfo ?: return 0
        val wifi = networkInfo.type == ConnectivityManager.TYPE_WIFI
        val prefs = getPreferences(context)
        return prefs.getString(
            if (wifi) Constants.PREFERENCES_KEY_MAX_BITRATE_WIFI else Constants.PREFERENCES_KEY_MAX_BITRATE_MOBILE,
            "0"
        )!!.toInt()
    }

    @JvmStatic
	fun getMaxVideoBitrate(context: Context): Int {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = manager.activeNetworkInfo ?: return 0
        val wifi = networkInfo.type == ConnectivityManager.TYPE_WIFI
        val prefs = getPreferences(context)
        return prefs.getString(
            if (wifi) Constants.PREFERENCES_KEY_MAX_VIDEO_BITRATE_WIFI else Constants.PREFERENCES_KEY_MAX_VIDEO_BITRATE_MOBILE,
            "0"
        )!!.toInt()
    }

    @JvmStatic
	fun getPreloadCount(context: Context): Int {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = manager.activeNetworkInfo ?: return 3
        val prefs = getPreferences(context)
        val wifi = networkInfo.type == ConnectivityManager.TYPE_WIFI
        val preloadCount = prefs.getString(
            if (wifi) Constants.PREFERENCES_KEY_PRELOAD_COUNT_WIFI else Constants.PREFERENCES_KEY_PRELOAD_COUNT_MOBILE,
            "-1"
        )!!.toInt()
        return if (preloadCount == -1) Int.MAX_VALUE else preloadCount
    }

    @JvmStatic
	fun getCacheSizeMB(context: Context): Int {
        val prefs = getPreferences(context)
        val cacheSize = prefs.getString(Constants.PREFERENCES_KEY_CACHE_SIZE, "-1")!!
            .toInt()
        return if (cacheSize == -1) Int.MAX_VALUE else cacheSize
    }

    @JvmStatic
	fun isBatchMode(context: Context): Boolean {
        return getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_BATCH_MODE, false)
    }

    @JvmStatic
	fun setBatchMode(context: Context, batchMode: Boolean) {
        getPreferences(context).edit().putBoolean(Constants.PREFERENCES_KEY_BATCH_MODE, batchMode)
            .commit()
    }

    @JvmStatic
	fun getRestUrl(context: Context, method: String?): String {
        return getRestUrl(context, method, true)
    }

    @JvmStatic
	fun getRestUrl(context: Context, method: String?, allowAltAddress: Boolean): String {
        val prefs = getPreferences(context)
        val instance = prefs.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1)
        return getRestUrl(context, method, prefs, instance, allowAltAddress)
    }

    fun getRestUrl(context: Context, method: String?, instance: Int): String {
        return getRestUrl(context, method, instance, true)
    }

    @JvmStatic
	fun getRestUrl(
        context: Context,
        method: String?,
        instance: Int,
        allowAltAddress: Boolean
    ): String {
        val prefs = getPreferences(context)
        return getRestUrl(context, method, prefs, instance, allowAltAddress)
    }

    fun getRestUrl(
        context: Context,
        method: String?,
        prefs: SharedPreferences,
        instance: Int
    ): String {
        return getRestUrl(context, method, prefs, instance, true)
    }

    fun getRestUrl(
        context: Context,
        method: String?,
        prefs: SharedPreferences,
        instance: Int,
        allowAltAddress: Boolean
    ): String {
        val builder = StringBuilder()
        var serverUrl = prefs.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null)
        if (allowAltAddress && isWifiConnected(context)) {
            val SSID =
                prefs.getString(Constants.PREFERENCES_KEY_SERVER_LOCAL_NETWORK_SSID + instance, "")
            if (!SSID!!.isEmpty()) {
                val currentSSID = getSSID(context)
                val ssidParts = SSID.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                if (SSID == currentSSID || Arrays.asList(*ssidParts).contains(currentSSID)) {
                    val internalUrl = prefs.getString(
                        Constants.PREFERENCES_KEY_SERVER_INTERNAL_URL + instance,
                        ""
                    )
                    if (!internalUrl!!.isEmpty() && "http://" != internalUrl) {
                        serverUrl = internalUrl
                    }
                }
            }
        }
        val username = prefs.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null)
        var password = prefs.getString(Constants.PREFERENCES_KEY_PASSWORD + instance, null)
        if (password != null && prefs.getBoolean(
                Constants.PREFERENCES_KEY_ENCRYPTED_PASSWORD + instance,
                false
            )
        ) password = KeyStoreUtil.decrypt(password)
        builder.append(serverUrl)
        if (builder[builder.length - 1] != '/') {
            builder.append("/")
        }
        if (method != null && ServerInfo.isMadsonic6(context, instance)) {
            builder.append("rest2/")
        } else {
            builder.append("rest/")
        }
        builder.append(method).append(".view")
        builder.append("?u=").append(username)
        if (method != null && ServerInfo.canUseToken(context, instance)) {
            val hash = (username + password).hashCode()
            var values = tokens[hash]
            if (values == null) {
                val salt = BigInteger(130, random).toString(32)
                val token = md5Hex(password + salt)
                values = Pair(salt, token)
                tokens.put(hash, values)
            }
            builder.append("&s=").append(values.first)
            builder.append("&t=").append(values.second)
        } else {
            // Slightly obfuscate password
            password = "enc:" + utf8HexEncode(password)
            builder.append("&p=").append(password)
        }
        if (method != null && ServerInfo.isMadsonic6(context, instance)) {
            builder.append("&v=").append(Constants.REST_PROTOCOL_VERSION_MADSONIC)
        } else {
            builder.append("&v=").append(Constants.REST_PROTOCOL_VERSION_SUBSONIC)
        }
        builder.append("&c=").append(Constants.REST_CLIENT_ID)
        return builder.toString()
    }

    @JvmStatic
	fun getRestUrlHash(context: Context): Int {
        return getRestUrlHash(context, getMostRecentActiveServer(context))
    }

    @JvmStatic
	fun getRestUrlHash(context: Context, instance: Int): Int {
        val builder = StringBuilder()
        val prefs = getPreferences(context)
        builder.append(prefs.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null))
        builder.append(prefs.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null))
        return builder.toString().hashCode()
    }

    fun getBlockTokenUsePref(context: Context, instance: Int): String {
        return Constants.CACHE_BLOCK_TOKEN_USE + getRestUrl(context, null, instance, false)
    }

    @JvmStatic
	fun getBlockTokenUse(context: Context, instance: Int): Boolean {
        return getPreferences(context).getBoolean(getBlockTokenUsePref(context, instance), false)
    }

    @JvmStatic
	fun setBlockTokenUse(context: Context, instance: Int, block: Boolean) {
        val editor = getPreferences(context).edit()
        editor.putBoolean(getBlockTokenUsePref(context, instance), block)
        editor.commit()
    }

    @JvmStatic
	fun replaceInternalUrl(context: Context, url: String): String {
        // Only change to internal when using https
        var url = url
        if (url.indexOf("https") != -1) {
            val prefs = getPreferences(context)
            val instance = prefs.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1)
            val internalUrl =
                prefs.getString(Constants.PREFERENCES_KEY_SERVER_INTERNAL_URL + instance, null)
            if (internalUrl != null && "" != internalUrl) {
                val externalUrl =
                    prefs.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null)
                url = url.replace(internalUrl, externalUrl!!)
            }
        }

        //  Use separate profile for Chromecast so users can do ogg on phone, mp3 for CC
        return url.replace("c=" + Constants.REST_CLIENT_ID, "c=" + Constants.CHROMECAST_CLIENT_ID)
    }

    @JvmStatic
	fun isTagBrowsing(context: Context): Boolean {
        return isTagBrowsing(context, getActiveServer(context))
    }

    @JvmStatic
	fun isTagBrowsing(context: Context, instance: Int): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(Constants.PREFERENCES_KEY_BROWSE_TAGS + instance, false)
    }

    @JvmStatic
	fun isSyncEnabled(context: Context, instance: Int): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SERVER_SYNC + instance, true)
    }

    @JvmStatic
	fun isAuthHeaderEnabled(context: Context, instance: Int): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SERVER_AUTHHEADER + instance, true)
    }

    fun getParentFromEntry(context: Context, entry: MusicDirectory.Entry): String? {
        return if (isTagBrowsing(context)) {
            if (!entry.isDirectory) {
                entry.albumId
            } else if (entry.isAlbum()) {
                entry.artistId
            } else {
                null
            }
        } else {
            entry.parent
        }
    }

    fun openToTab(context: Context): String? {
        val prefs = getPreferences(context)
        return prefs.getString(Constants.PREFERENCES_KEY_OPEN_TO_TAB, null)
    }

    @JvmStatic
	fun getVideoPlayerType(context: Context): String? {
        val prefs = getPreferences(context)
        return prefs.getString(Constants.PREFERENCES_KEY_VIDEO_PLAYER, "raw")
    }

    @JvmStatic
	fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.PREFERENCES_FILE_NAME, 0)
    }

    @JvmStatic
	fun getOfflineSync(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.OFFLINE_SYNC_NAME, 0)
    }

    fun getSyncDefault(context: Context): String? {
        val prefs = getOfflineSync(context)
        return prefs.getString(Constants.OFFLINE_SYNC_DEFAULT, null)
    }

    fun setSyncDefault(context: Context, defaultValue: String?) {
        val editor = getOfflineSync(context).edit()
        editor.putString(Constants.OFFLINE_SYNC_DEFAULT, defaultValue)
        editor.commit()
    }

    @JvmStatic
	fun getCacheName(context: Context, name: String, id: String): String {
        return getCacheName(context, getActiveServer(context), name, id)
    }

    @JvmStatic
	fun getCacheName(context: Context, instance: Int, name: String, id: String): String {
        val s = getRestUrl(context, null, instance, false) + id
        return name + "-" + s.hashCode() + ".ser"
    }

    @JvmStatic
	fun getCacheName(context: Context, name: String): String {
        return getCacheName(context, getActiveServer(context), name)
    }

    fun getCacheName(context: Context, instance: Int, name: String): String {
        val s = getRestUrl(context, null, instance, false)
        return name + "-" + s.hashCode() + ".ser"
    }

    fun offlineScrobblesCount(context: Context): Int {
        val offline = getOfflineSync(context)
        return offline.getInt(Constants.OFFLINE_SCROBBLE_COUNT, 0)
    }

    fun offlineStarsCount(context: Context): Int {
        val offline = getOfflineSync(context)
        return offline.getInt(Constants.OFFLINE_STAR_COUNT, 0)
    }

    @JvmStatic
	fun parseOfflineIDSearch(context: Context?, id: String, cacheLocation: String?): String {
        // Try to get this info based off of tags first
        var name = parseOfflineIDSearch(id)
        if (name != null) {
            return name
        }

        // Otherwise go nuts trying to parse from file structure
        name = id.replace(cacheLocation!!, "")
        if (name.startsWith("/")) {
            name = name.substring(1)
        }
        name = name.replace(".complete", "").replace(".partial", "")
        val index = name.lastIndexOf(".")
        name = if (index == -1) name else name.substring(0, index)
        val details = name.split("/".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        var title = details[details.size - 1]
        if (index == -1) {
            if (details.size > 1) {
                val artist = "artist:\"" + details[details.size - 2] + "\""
                val simpleArtist = "artist:\"$title\""
                title = "album:\"$title\""
                name = if (details[details.size - 1] == details[details.size - 2]) {
                    title
                } else {
                    "($artist AND $title) OR $simpleArtist"
                }
            } else {
                name = "artist:\"$title\" OR album:\"$title\""
            }
        } else {
            val artist: String
            artist = if (details.size > 2) {
                "artist:\"" + details[details.size - 3] + "\""
            } else {
                "(artist:\"" + details[0] + "\" OR album:\"" + details[0] + "\")"
            }
            title = "title:\"" + title.substring(title.indexOf('-') + 1) + "\""
            name = "$artist AND $title"
        }
        return name
    }

    fun parseOfflineIDSearch(id: String?): String? {
        val entry = MusicDirectory.Entry()
        val file = File(id)
        return if (file.exists()) {
            entry.loadMetadata(file)
            if (entry.artist != null) {
                var title = file.name
                title = title.replace(".complete", "").replace(".partial", "")
                val index = title.lastIndexOf(".")
                title = if (index == -1) title else title.substring(0, index)
                title = title.substring(title.indexOf('-') + 1)
                "artist:\"" + entry.artist + "\"" +
                        " AND title:\"" + title + "\""
            } else {
                null
            }
        } else {
            null
        }
    }

    @JvmStatic
	fun getRemainingTrialDays(context: Context): Int {
        val prefs = getPreferences(context)
        var installTime = prefs.getLong(Constants.PREFERENCES_KEY_INSTALL_TIME, 0L)
        if (installTime == 0L) {
            installTime = System.currentTimeMillis()
            val editor = prefs.edit()
            editor.putLong(Constants.PREFERENCES_KEY_INSTALL_TIME, installTime)
            editor.commit()
        }
        val now = System.currentTimeMillis()
        val millisPerDay = 24L * 60L * 60L * 1000L
        val daysSinceInstall = ((now - installTime) / millisPerDay).toInt()
        return Math.max(0, Constants.FREE_TRIAL_DAYS - daysSinceInstall)
    }

    @JvmStatic
	fun isCastProxy(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(Constants.PREFERENCES_KEY_CAST_PROXY, false)
    }

    @JvmStatic
	fun isFirstLevelArtist(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(
            Constants.PREFERENCES_KEY_FIRST_LEVEL_ARTIST + getActiveServer(
                context
            ), true
        )
    }

    @JvmStatic
	fun toggleFirstLevelArtist(context: Context) {
        val prefs = getPreferences(context)
        val editor = prefs.edit()
        if (prefs.getBoolean(
                Constants.PREFERENCES_KEY_FIRST_LEVEL_ARTIST + getActiveServer(context),
                true
            )
        ) {
            editor.putBoolean(
                Constants.PREFERENCES_KEY_FIRST_LEVEL_ARTIST + getActiveServer(context),
                false
            )
        } else {
            editor.putBoolean(
                Constants.PREFERENCES_KEY_FIRST_LEVEL_ARTIST + getActiveServer(context),
                true
            )
        }
        editor.commit()
    }

    @JvmStatic
	fun shouldCacheDuringCasting(context: Context): Boolean {
        return getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_CAST_CACHE, false)
    }

    @JvmStatic
	fun getSongPressAction(context: Context): String? {
        return getPreferences(context).getString(Constants.PREFERENCES_KEY_SONG_PRESS_ACTION, "all")
    }

    /**
     * Get the contents of an `InputStream` as a `byte[]`.
     *
     *
     * This method buffers the input internally, so there is no need to use a
     * `BufferedInputStream`.
     *
     * @param input the `InputStream` to read from
     * @return the requested byte array
     * @throws NullPointerException if the input is null
     * @throws IOException          if an I/O error occurs
     */
    @JvmStatic
	@Throws(IOException::class)
    fun toByteArray(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        copy(input, output)
        return output.toByteArray()
    }

    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(1024 * 4)
        var count: Long = 0
        var n: Int
        while (-1 != input.read(buffer).also { n = it }) {
            output.write(buffer, 0, n)
            count += n.toLong()
        }
        return count
    }

    @JvmStatic
	@Throws(IOException::class)
    fun renameFile(from: File, to: File) {
        if (!from.renameTo(to)) {
            Log.i(TAG, "Failed to rename $from to $to")
        }
    }

    @JvmStatic
	fun close(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (x: Throwable) {
            // Ignored
        }
    }

    @JvmStatic
	fun delete(file: File?): Boolean {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete file $file")
                return false
            }
            Log.i(TAG, "Deleted file $file")
        }
        return true
    }

    @JvmStatic
	@JvmOverloads
    fun toast(context: Context, messageId: Int, shortDuration: Boolean = true) {
        toast(context, context.getString(messageId), shortDuration)
    }

    @JvmStatic
	@JvmOverloads
    fun toast(context: Context?, message: String?, shortDuration: Boolean = true) {
        if (toast == null) {
            toast = Toast.makeText(
                context,
                message,
                if (shortDuration) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).apply {
                setGravity(Gravity.CENTER, 0, 0)
            }
        } else {
            toast!!.setText(message)
            toast!!.duration =
                if (shortDuration) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        }
        toast!!.show()
    }

    @JvmStatic
	fun confirmDialog(
        context: Context,
        action: Int,
        subject: Int,
        onClick: DialogInterface.OnClickListener?
    ) {
        confirmDialog(
            context,
            context.resources.getString(action).lowercase(Locale.getDefault()),
            context.resources.getString(subject),
            onClick,
            null
        )
    }

    fun confirmDialog(
        context: Context,
        action: Int,
        subject: Int,
        onClick: DialogInterface.OnClickListener?,
        onCancel: DialogInterface.OnClickListener?
    ) {
        confirmDialog(
            context,
            context.resources.getString(action).lowercase(Locale.getDefault()),
            context.resources.getString(subject),
            onClick,
            onCancel
        )
    }

    @JvmStatic
	fun confirmDialog(
        context: Context,
        action: Int,
        subject: String?,
        onClick: DialogInterface.OnClickListener?
    ) {
        confirmDialog(
            context,
            context.resources.getString(action).lowercase(Locale.getDefault()),
            subject,
            onClick,
            null
        )
    }

    fun confirmDialog(
        context: Context,
        action: Int,
        subject: String?,
        onClick: DialogInterface.OnClickListener?,
        onCancel: DialogInterface.OnClickListener?
    ) {
        confirmDialog(
            context,
            context.resources.getString(action).lowercase(Locale.getDefault()),
            subject,
            onClick,
            onCancel
        )
    }

    fun confirmDialog(
        context: Context,
        action: String?,
        subject: String?,
        onClick: DialogInterface.OnClickListener?,
        onCancel: DialogInterface.OnClickListener?
    ) {
        AlertDialog.Builder(context)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.common_confirm)
            .setMessage(
                context.resources.getString(
                    R.string.common_confirm_message,
                    action,
                    subject
                )
            )
            .setPositiveButton(R.string.common_ok, onClick)
            .setNegativeButton(R.string.common_cancel, onCancel)
            .show()
    }

    /**
     * Converts a byte-count to a formatted string suitable for display to the user.
     * For instance:
     *
     *  * `format(918)` returns *"918 B"*.
     *  * `format(98765)` returns *"96 KB"*.
     *  * `format(1238476)` returns *"1.2 MB"*.
     *
     * This method assumes that 1 KB is 1024 bytes.
     * To get a localized string, please use formatLocalizedBytes instead.
     *
     * @param byteCount The number of bytes.
     * @return The formatted string.
     */
    @JvmStatic
	@Synchronized
    fun formatBytes(byteCount: Long): String {

        // More than 1 GB?
        if (byteCount >= 1024 * 1024 * 1024) {
            val gigaByteFormat: NumberFormat = GIGA_BYTE_FORMAT
            return gigaByteFormat.format(byteCount.toDouble() / (1024 * 1024 * 1024))
        }

        // More than 1 MB?
        if (byteCount >= 1024 * 1024) {
            val megaByteFormat: NumberFormat = MEGA_BYTE_FORMAT
            return megaByteFormat.format(byteCount.toDouble() / (1024 * 1024))
        }

        // More than 1 KB?
        if (byteCount >= 1024) {
            val kiloByteFormat: NumberFormat = KILO_BYTE_FORMAT
            return kiloByteFormat.format(byteCount.toDouble() / 1024)
        }
        return "$byteCount B"
    }

    /**
     * Converts a byte-count to a formatted string suitable for display to the user.
     * For instance:
     *
     *  * `format(918)` returns *"918 B"*.
     *  * `format(98765)` returns *"96 KB"*.
     *  * `format(1238476)` returns *"1.2 MB"*.
     *
     * This method assumes that 1 KB is 1024 bytes.
     * This version of the method returns a localized string.
     *
     * @param byteCount The number of bytes.
     * @return The formatted string.
     */
    @JvmStatic
	@Synchronized
    fun formatLocalizedBytes(byteCount: Long, context: Context): String {

        // More than 1 GB?
        if (byteCount >= 1024 * 1024 * 1024) {
            if (GIGA_BYTE_LOCALIZED_FORMAT == null) {
                GIGA_BYTE_LOCALIZED_FORMAT =
                    DecimalFormat(context.resources.getString(R.string.util_bytes_format_gigabyte))
            }
            return GIGA_BYTE_LOCALIZED_FORMAT!!.format(byteCount.toDouble() / (1024 * 1024 * 1024))
        }

        // More than 1 MB?
        if (byteCount >= 1024 * 1024) {
            if (MEGA_BYTE_LOCALIZED_FORMAT == null) {
                MEGA_BYTE_LOCALIZED_FORMAT =
                    DecimalFormat(context.resources.getString(R.string.util_bytes_format_megabyte))
            }
            return MEGA_BYTE_LOCALIZED_FORMAT!!.format(byteCount.toDouble() / (1024 * 1024))
        }

        // More than 1 KB?
        if (byteCount >= 1024) {
            if (KILO_BYTE_LOCALIZED_FORMAT == null) {
                KILO_BYTE_LOCALIZED_FORMAT =
                    DecimalFormat(context.resources.getString(R.string.util_bytes_format_kilobyte))
            }
            return KILO_BYTE_LOCALIZED_FORMAT!!.format(byteCount.toDouble() / 1024)
        }
        if (BYTE_LOCALIZED_FORMAT == null) {
            BYTE_LOCALIZED_FORMAT =
                DecimalFormat(context.resources.getString(R.string.util_bytes_format_byte))
        }
        return BYTE_LOCALIZED_FORMAT!!.format(byteCount.toDouble())
    }

    @JvmStatic
	fun formatDuration(seconds: Int?): String? {
        if (seconds == null) {
            return null
        }
        val hours = seconds / 3600
        val minutes = seconds / 60 % 60
        val secs = seconds % 60
        val builder = StringBuilder(7)
        if (hours > 0) {
            builder.append(hours).append(":")
            if (minutes < 10) {
                builder.append("0")
            }
        }
        builder.append(minutes).append(":")
        if (secs < 10) {
            builder.append("0")
        }
        builder.append(secs)
        return builder.toString()
    }

    @JvmStatic
	fun parseDate(context: Context?, dateString: String?): Date? {
        var dateString = dateString ?: return null
        return try {
            dateString = dateString.replace(' ', 'T')
            val isDateNormalized = ServerInfo.checkServerVersion(context, "1.11")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
            if (isDateNormalized) {
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            }
            dateFormat.parse(dateString)
        } catch (e: ParseException) {
            Log.e(TAG, "Failed to parse date string", e)
            null
        }
    }

    @JvmStatic
	@JvmOverloads
    fun formatDate(context: Context?, dateString: String?, includeTime: Boolean = true): String {
        return if (dateString == null) {
            ""
        } else formatDate(
            parseDate(
                context,
                dateString
            ), includeTime
        )
    }

    @JvmStatic
	@JvmOverloads
    fun formatDate(date: Date?, includeTime: Boolean = true): String {
        return if (date == null) {
            "Never"
        } else {
            if (includeTime) {
                if (date.year != CURRENT_YEAR) {
                    DATE_FORMAT_LONG.format(date)
                } else {
                    DATE_FORMAT_SHORT.format(date)
                }
            } else {
                DATE_FORMAT_NO_TIME.format(date)
            }
        }
    }

    @JvmStatic
	fun formatDate(millis: Long): String {
        return formatDate(Date(millis))
    }

    @JvmStatic
	fun formatBoolean(context: Context, value: Boolean): String {
        return context.resources.getString(if (value) R.string.common_true else R.string.common_false)
    }

    @JvmStatic
	fun equals(object1: Any?, object2: Any?): Boolean {
        if (object1 === object2) {
            return true
        }
        return if (object1 == null || object2 == null) {
            false
        } else object1 == object2
    }

    /**
     * Encodes the given string by using the hexadecimal representation of its UTF-8 bytes.
     *
     * @param s The string to encode.
     * @return The encoded string.
     */
    fun utf8HexEncode(s: String?): String? {
        if (s == null) {
            return null
        }
        val utf8: ByteArray
        utf8 = try {
            s.toByteArray(charset(Constants.UTF_8))
        } catch (x: UnsupportedEncodingException) {
            throw RuntimeException(x)
        }
        return hexEncode(utf8)
    }

    /**
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     * The returned array will be double the length of the passed array, as it takes two characters to represent any
     * given byte.
     *
     * @param data Bytes to convert to hexadecimal characters.
     * @return A string containing hexadecimal characters.
     */
    fun hexEncode(data: ByteArray): String {
        val length = data.size
        val out = CharArray(length shl 1)
        // two characters form the hex value.
        var i = 0
        var j = 0
        while (i < length) {
            out[j++] = HEX_DIGITS[0xF0 and data[i].toInt() ushr 4]
            out[j++] = HEX_DIGITS[0x0F and data[i].toInt()]
            i++
        }
        return String(out)
    }

    /**
     * Calculates the MD5 digest and returns the value as a 32 character hex string.
     *
     * @param s Data to digest.
     * @return MD5 digest as a hex string.
     */
	@JvmStatic
	fun md5Hex(s: String?): String? {
        return if (s == null) {
            null
        } else try {
            val md5 = MessageDigest.getInstance("MD5")
            hexEncode(md5.digest(s.toByteArray(charset(Constants.UTF_8))))
        } catch (x: Exception) {
            throw RuntimeException(x.message, x)
        }
    }

    fun isNullOrWhiteSpace(string: String?): Boolean {
        return string == null || "" == string || "" == string.trim { it <= ' ' }
    }

    @JvmStatic
	fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and
            // width
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())

            // Choose the smallest ratio as inSampleSize value, this will
            // guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }
        return inSampleSize
    }

    @JvmStatic
	fun getScaledHeight(height: Double, width: Double, newWidth: Int): Int {
        // Try to keep correct aspect ratio of the original image, do not force a square
        val aspectRatio = height / width

        // Assume the size given refers to the width of the image, so calculate the new height using
        //	the previously determined aspect ratio
        return Math.round(newWidth * aspectRatio).toInt()
    }

    @JvmStatic
	fun getScaledHeight(bitmap: Bitmap, width: Int): Int {
        return getScaledHeight(bitmap.height.toDouble(), bitmap.width.toDouble(), width)
    }

    @JvmStatic
	fun getStringDistance(s: CharSequence?, t: CharSequence?): Int {
        var s = s
        var t = t
        require(!(s == null || t == null)) { "Strings must not be null" }
        if (t.toString().lowercase(Locale.getDefault())
                .indexOf(s.toString().lowercase(Locale.getDefault())) != -1
        ) {
            return 1
        }
        var n = s.length
        var m = t.length
        if (n == 0) {
            return m
        } else if (m == 0) {
            return n
        }
        if (n > m) {
            val tmp: CharSequence = s
            s = t
            t = tmp
            n = m
            m = t.length
        }
        var p = IntArray(n + 1)
        var d = IntArray(n + 1)
        var _d: IntArray
        var i: Int
        var j: Int
        var t_j: Char
        var cost: Int
        i = 0
        while (i <= n) {
            p[i] = i
            i++
        }
        j = 1
        while (j <= m) {
            t_j = t[j - 1]
            d[0] = j
            i = 1
            while (i <= n) {
                cost = if (s[i - 1] == t_j) 0 else 1
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost)
                i++
            }
            _d = p
            p = d
            d = _d
            j++
        }
        return p[n]
    }

    @JvmStatic
	fun isNetworkConnected(context: Context): Boolean {
        return isNetworkConnected(context, false)
    }

    fun isNetworkConnected(context: Context, streaming: Boolean): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = manager.activeNetworkInfo
        val connected = networkInfo != null && networkInfo.isConnected
        return if (streaming) {
            val wifiConnected =
                connected && networkInfo!!.type == ConnectivityManager.TYPE_WIFI
            val wifiRequired = isWifiRequiredForDownload(context)
            val isLocalNetwork = connected && !networkInfo!!.isRoaming
            val localNetworkRequired =
                isLocalNetworkRequiredForDownload(context)
            connected && (!wifiRequired || wifiConnected) && (!localNetworkRequired || isLocalNetwork)
        } else {
            connected
        }
    }

    fun isWifiConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = manager.activeNetworkInfo
        val connected = networkInfo != null && networkInfo.isConnected
        return connected && networkInfo!!.type == ConnectivityManager.TYPE_WIFI
    }

    @JvmStatic
	fun getSSID(context: Context): String? {
        if (isWifiConnected(context)) {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            return if (wifiManager.connectionInfo != null && wifiManager.connectionInfo.ssid != null) {
                wifiManager.connectionInfo.ssid.replace("\"", "")
            } else null
        }
        return null
    }

    @JvmStatic
	val isExternalStoragePresent: Boolean
        get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

    @JvmStatic
	fun isAllowedToDownload(context: Context): Boolean {
        return isNetworkConnected(context, true) && !isOffline(context)
    }

    @JvmStatic
	fun isWifiRequiredForDownload(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(Constants.PREFERENCES_KEY_WIFI_REQUIRED_FOR_DOWNLOAD, false)
    }

    @JvmStatic
	fun isLocalNetworkRequiredForDownload(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.getBoolean(
            Constants.PREFERENCES_KEY_LOCAL_NETWORK_REQUIRED_FOR_DOWNLOAD,
            false
        )
    }

    @JvmOverloads
    fun info(context: Context, titleId: Int, messageId: Int, linkify: Boolean = true) {
        showDialog(context, android.R.drawable.ic_dialog_info, titleId, messageId, linkify)
    }

    @JvmOverloads
    fun info(context: Context, titleId: Int, message: String?, linkify: Boolean = true) {
        showDialog(context, android.R.drawable.ic_dialog_info, titleId, message, linkify)
    }

    @JvmOverloads
    fun info(context: Context?, title: String?, message: String?, linkify: Boolean = true) {
        showDialog(context, android.R.drawable.ic_dialog_info, title, message, linkify)
    }

    @JvmOverloads
    fun showDialog(
        context: Context,
        icon: Int,
        titleId: Int,
        messageId: Int,
        linkify: Boolean = true
    ) {
        showDialog(
            context,
            icon,
            context.resources.getString(titleId),
            context.resources.getString(messageId),
            linkify
        )
    }

    @JvmOverloads
    fun showDialog(
        context: Context,
        icon: Int,
        titleId: Int,
        message: String?,
        linkify: Boolean = true
    ) {
        showDialog(context, icon, context.resources.getString(titleId), message, linkify)
    }

    @JvmOverloads
    fun showDialog(
        context: Context?,
        icon: Int,
        title: String?,
        message: String?,
        linkify: Boolean = true
    ) {
        val ss = SpannableString(message)
        if (linkify) {
            Linkify.addLinks(ss, Linkify.ALL)
        }
        val dialog = AlertDialog.Builder(
            context!!
        )
            .setIcon(icon)
            .setTitle(title)
            .setMessage(ss)
            .setPositiveButton(R.string.common_ok) { dialog, i -> dialog.dismiss() }
            .show()
        (dialog.findViewById<View>(android.R.id.message) as TextView?)!!.movementMethod =
            LinkMovementMethod.getInstance()
    }

    @JvmStatic
	fun showHTMLDialog(context: Context, title: Int, message: Int) {
        showHTMLDialog(context, title, context.resources.getString(message))
    }

    fun showHTMLDialog(context: Context?, title: Int, message: String?) {
        val dialog = AlertDialog.Builder(
            context!!
        )
            .setIcon(android.R.drawable.ic_dialog_info)
            .setTitle(title)
            .setMessage(Html.fromHtml(message))
            .setPositiveButton(R.string.common_ok) { dialog, i -> dialog.dismiss() }
            .show()
        (dialog.findViewById<View>(android.R.id.message) as TextView?)!!.movementMethod =
            LinkMovementMethod.getInstance()
    }

    @JvmStatic
    fun showDetailsDialog(
        context: Context,
        @StringRes title: Int,
        headers: List<Int?>,
        details: List<String?>?
    ) {
        val headerStrings: MutableList<String> = ArrayList()
        for (@StringRes res in headers) {
            headerStrings.add(context.resources.getString(res!!))
        }
        showDetailsDialog(context, context.resources.getString(title), headerStrings, details)
    }

    fun showDetailsDialog(
        context: Context,
        title: String?,
        headers: List<String>?,
        details: List<String?>?
    ) {
        val listView = ListView(context)
        listView.adapter = DetailsAdapter(context, R.layout.details_item, headers, details)
        listView.divider = null
        listView.isScrollbarFadingEnabled = false

        // Let the user long-click on a row to copy its value to the clipboard
        listView.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { parent, view, pos, id ->
                val nameView = view.findViewById<View>(R.id.detail_name) as TextView
                val detailsView = view.findViewById<View>(R.id.detail_value) as TextView
                if (nameView == null || detailsView == null) {
                    return@OnItemLongClickListener false
                }
                val name = nameView.text
                val value = detailsView.text
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(name, value)
                clipboard.setPrimaryClip(clip)
                toast(context, "Copied $name to clipboard")
                true
            }
        AlertDialog.Builder(context) // .setIcon(android.R.drawable.ic_dialog_info)
            .setTitle(title)
            .setView(listView)
            .setPositiveButton(R.string.common_close) { dialog, i -> dialog.dismiss() }
            .show()
    }

    @JvmStatic
	fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (x: InterruptedException) {
            Log.w(TAG, "Interrupted from sleep.", x)
        }
    }

    fun startActivityWithoutTransition(
        currentActivity: Activity,
        newActivitiy: Class<out Activity?>?
    ) {
        startActivityWithoutTransition(currentActivity, Intent(currentActivity, newActivitiy))
    }

    @JvmStatic
	fun startActivityWithoutTransition(currentActivity: Activity, intent: Intent?) {
        currentActivity.startActivity(intent)
        disablePendingTransition(currentActivity)
    }

    fun disablePendingTransition(activity: Activity?) {

        // Activity.overridePendingTransition() was introduced in Android 2.0.  Use reflection to maintain
        // compatibility with 1.5.
        try {
            val method = Activity::class.java.getMethod(
                "overridePendingTransition",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(activity, 0, 0)
        } catch (x: Throwable) {
            // Ignored
        }
    }

    @JvmStatic
	fun createDrawableFromBitmap(context: Context, bitmap: Bitmap?): Drawable {
        // BitmapDrawable(Resources, Bitmap) was introduced in Android 1.6.  Use reflection to maintain
        // compatibility with 1.5.
        return try {
            val constructor = BitmapDrawable::class.java.getConstructor(
                Resources::class.java, Bitmap::class.java
            )
            constructor.newInstance(context.resources, bitmap)
        } catch (x: Throwable) {
            BitmapDrawable(bitmap)
        }
    }

    @JvmStatic
	fun registerMediaButtonEventReceiver(context: Context) {

        // Only do it if enabled in the settings and api < 21
        val prefs = getPreferences(context)
        val enabled = prefs.getBoolean(Constants.PREFERENCES_KEY_MEDIA_BUTTONS, true)
        if (enabled && Build.VERSION.SDK_INT < 21) {

            // AudioManager.registerMediaButtonEventReceiver() was introduced in Android 2.2.
            // Use reflection to maintain compatibility with 1.5.
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val componentName =
                    ComponentName(context.packageName, MediaButtonIntentReceiver::class.java.name)
                val method = AudioManager::class.java.getMethod(
                    "registerMediaButtonEventReceiver",
                    ComponentName::class.java
                )
                method.invoke(audioManager, componentName)
            } catch (x: Throwable) {
                // Ignored.
            }
        }
    }

    @JvmStatic
	fun unregisterMediaButtonEventReceiver(context: Context) {
        // AudioManager.unregisterMediaButtonEventReceiver() was introduced in Android 2.2.
        // Use reflection to maintain compatibility with 1.5.
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val componentName =
                ComponentName(context.packageName, MediaButtonIntentReceiver::class.java.name)
            val method = AudioManager::class.java.getMethod(
                "unregisterMediaButtonEventReceiver",
                ComponentName::class.java
            )
            method.invoke(audioManager, componentName)
        } catch (x: Throwable) {
            // Ignored.
        }
    }

    @JvmStatic
    fun requestAudioFocus(context: Context, audioManager: AudioManager) {
        if (Build.VERSION.SDK_INT >= 26) {
            if (audioFocusRequest == null) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setOnAudioFocusChangeListener(
                        getAudioFocusChangeListener(
                            context,
                            audioManager
                        )
                    )
                    .setWillPauseWhenDucked(true)
                    .build()
                    .also {
                        audioManager.requestAudioFocus(it)
                    }
            }
        } else if (focusListener == null) {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                getAudioFocusChangeListener(
                    context,
                    audioManager
                ).also { focusListener = it },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun getAudioFocusChangeListener(
        context: Context,
        audioManager: AudioManager
    ): AudioManager.OnAudioFocusChangeListener {
        return object : AudioManager.OnAudioFocusChangeListener {
            override fun onAudioFocusChange(focusChange: Int) {
                val downloadService = context as DownloadService
                if ((focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) && !downloadService.isRemoteEnabled) {
                    if (downloadService.playerState == PlayerState.STARTED) {
                        Log.i(TAG, "Temporary loss of focus")
                        val prefs = getPreferences(context)
                        val lossPref = prefs.getString(Constants.PREFERENCES_KEY_TEMP_LOSS, "1")!!
                            .toInt()
                        if (lossPref == 2 || lossPref == 1 && focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                            lowerFocus = true
                            downloadService.setVolume(0.1f)
                        } else if (lossPref == 0 || lossPref == 1 && focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            pauseFocus = true
                            downloadService.pause(true)
                        }
                    }
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    if (pauseFocus) {
                        pauseFocus = false
                        downloadService.start()
                    }
                    if (lowerFocus) {
                        lowerFocus = false
                        downloadService.setVolume(1.0f)
                    }
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS && !downloadService.isRemoteEnabled) {
                    Log.i(TAG, "Permanently lost focus")
                    focusListener = null
                    downloadService.pause()
                    if (audioFocusRequest != null && Build.VERSION.SDK_INT >= 26) {
                        audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
                        audioFocusRequest = null
                    } else {
                        audioManager.abandonAudioFocus(this)
                    }
                }
            }
        }
    }

    fun abandonAudioFocus(context: Context) {
        if (focusListener != null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocus(focusListener)
            focusListener = null
        }
    }

    /**
     *
     * Broadcasts the given song info as the new song being played.
     */
	@JvmStatic
	fun broadcastNewTrackInfo(context: Context, song: MusicDirectory.Entry?) {
        try {
            val intent = Intent(EVENT_META_CHANGED)
            val avrcpIntent = Intent(AVRCP_METADATA_CHANGED)
            if (song != null) {
                intent.putExtra("title", song.title)
                intent.putExtra("artist", song.artist)
                intent.putExtra("album", song.album)
                val albumArtFile = FileUtil.getAlbumArtFile(context, song)
                intent.putExtra("coverart", albumArtFile.absolutePath)
                avrcpIntent.putExtra("playing", true)
            } else {
                intent.putExtra("title", "")
                intent.putExtra("artist", "")
                intent.putExtra("album", "")
                intent.putExtra("coverart", "")
                avrcpIntent.putExtra("playing", false)
            }
            addTrackInfo(context, song, avrcpIntent)
            context.sendBroadcast(intent)
            context.sendBroadcast(avrcpIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcastNewTrackInfo", e)
        }
    }

    /**
     *
     * Broadcasts the given player state as the one being set.
     */
	@JvmStatic
	fun broadcastPlaybackStatusChange(
        context: Context,
        song: MusicDirectory.Entry?,
        state: PlayerState
    ) {
        try {
            val intent = Intent(EVENT_PLAYSTATE_CHANGED)
            val avrcpIntent = Intent(AVRCP_PLAYSTATE_CHANGED)
            when (state) {
                PlayerState.STARTED -> {
                    intent.putExtra("state", "play")
                    avrcpIntent.putExtra("playing", true)
                }

                PlayerState.STOPPED -> {
                    intent.putExtra("state", "stop")
                    avrcpIntent.putExtra("playing", false)
                }

                PlayerState.PAUSED -> {
                    intent.putExtra("state", "pause")
                    avrcpIntent.putExtra("playing", false)
                }

                PlayerState.PREPARED ->                    // Only send quick pause event for samsung devices, causes issues for others
                    if (Build.MANUFACTURER.lowercase(Locale.getDefault())
                            .indexOf("samsung") != -1
                    ) {
                        avrcpIntent.putExtra("playing", false)
                    } else {
                        return  // Don't broadcast anything
                    }

                PlayerState.COMPLETED -> {
                    intent.putExtra("state", "complete")
                    avrcpIntent.putExtra("playing", false)
                }

                else -> return  // No need to broadcast.
            }
            addTrackInfo(context, song, avrcpIntent)
            if (state != PlayerState.PREPARED) {
                context.sendBroadcast(intent)
            }
            context.sendBroadcast(avrcpIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcastPlaybackStatusChange", e)
        }
    }

    private fun addTrackInfo(context: Context, song: MusicDirectory.Entry?, intent: Intent) {
        if (song != null) {
            val downloadService = context as DownloadService
            val albumArtFile = FileUtil.getAlbumArtFile(context, song)
            intent.putExtra("track", song.title)
            intent.putExtra("artist", song.artist)
            intent.putExtra("album", song.album)
            intent.putExtra("ListSize", downloadService.songs.size.toLong())
            intent.putExtra("id", downloadService.currentPlayingIndex.toLong() + 1)
            intent.putExtra("duration", downloadService.playerDuration.toLong())
            intent.putExtra("position", downloadService.playerPosition.toLong())
            intent.putExtra("coverart", albumArtFile.absolutePath)
            intent.putExtra("package", "github.daneren2005.dsub")
        } else {
            intent.putExtra("track", "")
            intent.putExtra("artist", "")
            intent.putExtra("album", "")
            intent.putExtra("ListSize", 0L)
            intent.putExtra("id", 0L)
            intent.putExtra("duration", 0L)
            intent.putExtra("position", 0L)
            intent.putExtra("coverart", "")
            intent.putExtra("package", "github.daneren2005.dsub")
        }
    }

    @JvmStatic
	fun createWifiLock(context: Context, tag: String?): WifiManager.WifiLock {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var lockType = WifiManager.WIFI_MODE_FULL
        if (Build.VERSION.SDK_INT >= 12) {
            lockType = 3
        }
        return wm.createWifiLock(lockType, tag)
    }
}