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

 Copyright 2010 (C) Sindre Mehus
 */
package github.daneren2005.dsub.provider

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import github.daneren2005.dsub.Preferences
import github.daneren2005.dsub.R
import github.daneren2005.dsub.activity.SubsonicActivity.Companion.getStaticImageLoader
import github.daneren2005.dsub.activity.SubsonicFragmentActivity
import github.daneren2005.dsub.domain.MusicDirectory
import github.daneren2005.dsub.domain.PlayerQueue
import github.daneren2005.dsub.service.DownloadService
import github.daneren2005.dsub.service.DownloadServiceLifecycleSupport
import github.daneren2005.dsub.util.Constants
import github.daneren2005.dsub.util.FileUtil
import github.daneren2005.dsub.util.Util.getPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Simple widget to show currently playing album art along
 * with play/pause and next track buttons.
 *
 *
 * Based on source code from the stock Android Music app.
 *
 * @author Sindre Mehus
 */
open class DSubWidgetProvider : AppWidgetProvider(), KoinComponent {
    private val prefs: Preferences.App by inject()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        defaultAppWidget(context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        notifyInstances(context, DownloadService.getInstance(), false)
    }

    protected open val layout: Int
        protected get() = 0

    /**
     * Initialize given widgets to default state, where we launch Subsonic on default click
     * and hide actions if service not running.
     */
    private fun defaultAppWidget(context: Context, appWidgetIds: IntArray) {
        val res = context.resources
        val views = RemoteViews(context.packageName, layout)
        views.setTextViewText(R.id.artist, res.getText(R.string.widget_initial_text))
        if (layout == R.layout.appwidget4x2) {
            views.setTextViewText(R.id.album, "")
        }
        linkButtons(context, views, false)
        performUpdate(context, null, appWidgetIds, false)
    }

    private fun pushUpdate(context: Context, appWidgetIds: IntArray?, views: RemoteViews) {
        // Update specific list of appWidgetIds if given, otherwise default to all
        val manager = AppWidgetManager.getInstance(context)
        if (appWidgetIds != null) {
            manager.updateAppWidget(appWidgetIds, views)
        } else {
            manager.updateAppWidget(ComponentName(context, this.javaClass), views)
        }
    }

    /**
     * Handle a change notification coming over from [DownloadService]
     */
    fun notifyChange(context: Context, service: DownloadService?, playing: Boolean) {
        if (hasInstances(context)) {
            performUpdate(context, service, null, playing)
        }
    }

    /**
     * Check against [AppWidgetManager] if there are any instances of this widget.
     */
    private fun hasInstances(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val appWidgetIds = manager.getAppWidgetIds(ComponentName(context, javaClass))
        return appWidgetIds.size > 0
    }

    /**
     * Update all active widget instances by pushing changes
     */
    private fun performUpdate(
        context: Context,
        service: DownloadService?,
        appWidgetIds: IntArray?,
        playing: Boolean
    ) {
        val res = context.resources
        val views = RemoteViews(context.packageName, layout)
        if (playing) {
            views.setViewVisibility(R.id.widget_root, View.VISIBLE)
        } else {
            // Hide widget
            if (prefs.get(R.string.key_hide_widget)) {
                views.setViewVisibility(R.id.widget_root, View.GONE)
            }
        }

        // Get Entry from current playing DownloadFile
        var currentPlaying: MusicDirectory.Entry? = null
        if (service == null) {
            // Deserialize from playling list to setup
            try {
                val state = FileUtil.deserialize(
                    context,
                    DownloadServiceLifecycleSupport.FILENAME_DOWNLOADS_SER,
                    PlayerQueue::class.java
                )
                if (state != null && state.currentPlayingIndex != -1) {
                    currentPlaying = state.songs[state.currentPlayingIndex]
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to grab current playing", e)
            }
        } else {
            currentPlaying =
                if (service.currentPlaying == null) null else service.currentPlaying.song
        }
        val title = currentPlaying?.title
        val artist: CharSequence? = currentPlaying?.artist
        val album: CharSequence? = currentPlaying?.album
        var errorState: CharSequence? = null

        // Show error message?
        val status = Environment.getExternalStorageState()
        if (status == Environment.MEDIA_SHARED || status == Environment.MEDIA_UNMOUNTED) {
            errorState = res.getText(R.string.widget_sdcard_busy)
        } else if (status == Environment.MEDIA_REMOVED) {
            errorState = res.getText(R.string.widget_sdcard_missing)
        } else if (currentPlaying == null) {
            errorState = res.getText(R.string.widget_initial_text)
        }
        if (errorState != null) {
            // Show error state to user
            views.setTextViewText(R.id.title, null)
            views.setTextViewText(R.id.artist, errorState)
            views.setTextViewText(R.id.album, "")
            if (layout != R.layout.appwidget4x1) {
                views.setImageViewResource(
                    R.id.appwidget_coverart,
                    R.drawable.appwidget_art_default
                )
            }
        } else {
            // No error, so show normal titles
            views.setTextViewText(R.id.title, title)
            views.setTextViewText(R.id.artist, artist)
            if (layout != R.layout.appwidget4x1) {
                views.setTextViewText(R.id.album, album)
            }
        }

        // Set correct drawable for pause state
        if (playing) {
            views.setImageViewResource(R.id.control_play, R.drawable.media_pause_dark)
        } else {
            views.setImageViewResource(R.id.control_play, R.drawable.media_start_dark)
        }

        // Set the cover art
        try {
            var large = false
            if (layout != R.layout.appwidget4x1 && layout != R.layout.appwidget4x2) {
                large = true
            }
            val imageLoader = getStaticImageLoader(context)
            var bitmap = imageLoader?.getCachedImage(context, currentPlaying, large)
            if (bitmap == null) {
                // Set default cover art
                views.setImageViewResource(
                    R.id.appwidget_coverart,
                    R.drawable.appwidget_art_unknown
                )
            } else {
                bitmap = getRoundedCornerBitmap(bitmap)
                views.setImageViewBitmap(R.id.appwidget_coverart, bitmap)
            }
        } catch (x: Exception) {
            Log.e(TAG, "Failed to load cover art", x)
            views.setImageViewResource(R.id.appwidget_coverart, R.drawable.appwidget_art_unknown)
        }

        // Link actions buttons to intents
        linkButtons(context, views, currentPlaying != null)
        pushUpdate(context, appWidgetIds, views)
    }

    /**
     * Link up various button actions using [PendingIntent].
     *
     * @param playerActive @param playerActive True if player is active in background.  Launch [github.daneren2005.dsub.activity.SubsonicFragmentActivity].
     */
    private fun linkButtons(context: Context, views: RemoteViews, playerActive: Boolean) {
        var intent = Intent(context, SubsonicFragmentActivity::class.java)
        intent.putExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        var pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.appwidget_coverart, pendingIntent)
        views.setOnClickPendingIntent(R.id.appwidget_top, pendingIntent)

        // Emulate media button clicks.
        intent = Intent("DSub.PLAY_PAUSE")
        intent.component = ComponentName(context, DownloadService::class.java)
        intent.action = DownloadService.CMD_TOGGLEPAUSE
        pendingIntent =
            if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            ) else PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        views.setOnClickPendingIntent(R.id.control_play, pendingIntent)
        intent =
            Intent("DSub.NEXT") // Use a unique action name to ensure a different PendingIntent to be created.
        intent.component = ComponentName(context, DownloadService::class.java)
        intent.action = DownloadService.CMD_NEXT
        pendingIntent =
            if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            ) else PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        views.setOnClickPendingIntent(R.id.control_next, pendingIntent)
        intent =
            Intent("DSub.PREVIOUS") // Use a unique action name to ensure a different PendingIntent to be created.
        intent.component = ComponentName(context, DownloadService::class.java)
        intent.action = DownloadService.CMD_PREVIOUS
        pendingIntent =
            if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            ) else PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        views.setOnClickPendingIntent(R.id.control_previous, pendingIntent)
    }

    companion object {
        private val TAG = DSubWidgetProvider::class.java.simpleName
        private var instance4x1: DSubWidget4x1? = null
        private var instance4x2: DSubWidget4x2? = null
        private var instance4x3: DSubWidget4x3? = null
        private var instance4x4: DSubWidget4x4? = null
        @JvmStatic
        @Synchronized
        fun notifyInstances(context: Context, service: DownloadService?, playing: Boolean) {
            if (instance4x1 == null) {
                instance4x1 = DSubWidget4x1()
            }
            if (instance4x2 == null) {
                instance4x2 = DSubWidget4x2()
            }
            if (instance4x3 == null) {
                instance4x3 = DSubWidget4x3()
            }
            if (instance4x4 == null) {
                instance4x4 = DSubWidget4x4()
            }
            instance4x1!!.notifyChange(context, service, playing)
            instance4x2!!.notifyChange(context, service, playing)
            instance4x3!!.notifyChange(context, service, playing)
            instance4x4!!.notifyChange(context, service, playing)
        }

        /**
         * Round the corners of a bitmap for the cover art image
         */
        private fun getRoundedCornerBitmap(bitmap: Bitmap): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val color = -0xbdbdbe
            val paint = Paint()
            val roundPx = 10f

            // Add extra width to the rect so the right side wont be rounded.
            val rect = Rect(0, 0, bitmap.width + roundPx.toInt(), bitmap.height)
            val rectF = RectF(rect)
            paint.isAntiAlias = true
            canvas.drawARGB(0, 0, 0, 0)
            paint.color = color
            canvas.drawRoundRect(rectF, roundPx, roundPx, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
            return output
        }
    }
}