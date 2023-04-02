/*
  This file is part of Subsonic.
	Subsonic is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	Subsonic is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.
	You should have received a copy of the GNU General Public License
	along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
	Copyright 2014 (C) Scott Jackson
*/
package github.daneren2005.dsub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import github.daneren2005.dsub.domain.PlayerState
import github.daneren2005.dsub.service.DownloadService
import github.daneren2005.dsub.util.Constants
import github.daneren2005.dsub.util.Util

class AudioNoisyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadService = DownloadService.getInstance() ?: return
        // Don't do anything if downloadService is not started
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
            if (!downloadService.isRemoteEnabled && (downloadService.playerState == PlayerState.STARTED || downloadService.playerState == PlayerState.PAUSED_TEMP)) {
                val prefs = Util.getPreferences(downloadService)
                val pausePref = prefs.getString(Constants.PREFERENCES_KEY_PAUSE_DISCONNECT, "0")!!
                    .toInt()
                if (pausePref == 0) {
                    downloadService.pause()
                }
            }
        }
    }

    companion object {
        private val TAG = AudioNoisyReceiver::class.java.simpleName
    }
}