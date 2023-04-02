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

	Copyright 2014 (C) Scott Jackson
*/
package github.daneren2005.dsub.audiofx

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

class LoudnessEnhancerController(private val context: Context, private val  audioSessionId: Int) {
    private var enhancer: LoudnessEnhancer? = null
    private var released = false

    init {
        try {
            enhancer = LoudnessEnhancer(audioSessionId)
        } catch (x: Throwable) {
            Log.w(TAG, "Failed to create enhancer", x)
        }
    }

    val isAvailable: Boolean
        get() = enhancer != null
    val isEnabled: Boolean
        get() = try {
            isAvailable && enhancer!!.enabled
        } catch (e: Exception) {
            false
        }

    fun enable() {
        enhancer!!.enabled = true
    }

    fun disable() {
        enhancer!!.enabled = false
    }

    val gain: Float
        get() = enhancer!!.targetGain

    fun setGain(gain: Int) {
        enhancer!!.setTargetGain(gain)
    }

    fun release() {
        if (isAvailable) {
            enhancer!!.release()
            released = true
        }
    }

    companion object {
        private val TAG = LoudnessEnhancerController::class.java.simpleName
    }
}