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

 Copyright 2011 (C) Sindre Mehus
 */
package github.daneren2005.dsub.audiofx

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import github.daneren2005.dsub.util.FileUtil
import java.io.Serializable

/**
 * Backward-compatible wrapper for [Equalizer], which is API Level 9.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
class EqualizerController(private val context: Context, private var audioSessionId: Int) {
    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var loudnessAvailable = false
    private var loudnessEnhancerController: LoudnessEnhancerController? = null
    private var released = false

    init {
        init()
    }

    private fun init() {
        equalizer = Equalizer(0, audioSessionId)
        bass = BassBoost(0, audioSessionId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            loudnessAvailable = true
            loudnessEnhancerController = LoudnessEnhancerController(context, audioSessionId)
        }
    }

    fun saveSettings() {
        try {
            if (isAvailable) {
                FileUtil.serialize(
                    context,
                    EqualizerSettings(equalizer, bass, loudnessEnhancerController),
                    "equalizer.dat"
                )
            }
        } catch (x: Throwable) {
            Log.w(TAG, "Failed to save equalizer settings.", x)
        }
    }

    fun loadSettings() {
        try {
            if (isAvailable) {
                val settings =
                    FileUtil.deserialize(context, "equalizer.dat", EqualizerSettings::class.java)
                settings?.apply(equalizer, bass, loudnessEnhancerController)
            }
        } catch (x: Throwable) {
            Log.w(TAG, "Failed to load equalizer settings.", x)
        }
    }

    val isAvailable: Boolean
        get() = equalizer != null && bass != null
    val isEnabled: Boolean
        get() = try {
            isAvailable && equalizer!!.enabled
        } catch (e: Exception) {
            false
        }

    fun release() {
        if (isAvailable) {
            released = true
            equalizer!!.release()
            bass!!.release()
            if (loudnessEnhancerController != null && loudnessEnhancerController!!.isAvailable) {
                loudnessEnhancerController!!.release()
            }
        }
    }

    fun getEqualizer(): Equalizer? {
        if (released) {
            released = false
            try {
                init()
            } catch (x: Throwable) {
                equalizer = null
                released = true
                Log.w(TAG, "Failed to create equalizer.", x)
            }
        }
        return equalizer
    }

    val bassBoost: BassBoost?
        get() {
            if (released) {
                released = false
                try {
                    init()
                } catch (x: Throwable) {
                    bass = null
                    Log.w(TAG, "Failed to create bass booster.", x)
                }
            }
            return bass
        }

    fun getLoudnessEnhancerController(): LoudnessEnhancerController? {
        if (loudnessAvailable && released) {
            released = false
            try {
                init()
            } catch (x: Throwable) {
                loudnessEnhancerController = null
                Log.w(TAG, "Failed to create loudness enhancer.", x)
            }
        }
        return loudnessEnhancerController
    }

    private class EqualizerSettings : Serializable {
        private lateinit var bandLevels: ShortArray
        private var preset: Short = 0
        private var enabled = false
        private var bass: Short = 0
        private var loudness = 0

        constructor()
        constructor(
            equalizer: Equalizer?,
            boost: BassBoost?,
            loudnessEnhancerController: LoudnessEnhancerController?
        ) {
            enabled = equalizer!!.enabled
            bandLevels = ShortArray(equalizer.numberOfBands.toInt())
            for (i in 0 until equalizer.numberOfBands) {
                bandLevels[i] = equalizer.getBandLevel(i.toShort())
            }
            preset = try {
                equalizer.currentPreset
            } catch (x: Exception) {
                -1
            }
            bass = try {
                boost!!.roundedStrength
            } catch (e: Exception) {
                0
            }
            loudness = try {
                loudnessEnhancerController?.gain?.toInt() ?: 0
            } catch (e: Exception) {
                0
            }
        }

        fun apply(
            equalizer: Equalizer?,
            boost: BassBoost?,
            loudnessController: LoudnessEnhancerController?
        ) {
            for (i in bandLevels.indices) {
                equalizer!!.setBandLevel(i.toShort(), bandLevels[i])
            }
            equalizer!!.enabled = enabled
            if (bass.toInt() != 0) {
                boost!!.enabled = true
                boost.setStrength(bass)
            }
            if (loudness != 0) {
                loudnessController!!.enable()
                loudnessController.setGain(loudness)
            }
        }
    }

    companion object {
        private val TAG = EqualizerController::class.java.simpleName
    }
}