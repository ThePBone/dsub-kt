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
	Copyright 2016 (C) Scott Jackson
*/
package github.daneren2005.dsub.util

import android.content.Context
import android.content.res.Configuration
import github.daneren2005.dsub.Preferences
import github.daneren2005.dsub.R
import github.daneren2005.dsub.activity.SettingsActivity
import github.daneren2005.dsub.activity.SubsonicFragmentActivity
import github.daneren2005.dsub.util.Util.getPreferences
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

object ThemeUtil : KoinComponent {
    private val prefs: Preferences.App by inject()

    const val THEME_DARK = "dark"
    const val THEME_BLACK = "black"
    const val THEME_LIGHT = "light"
    const val THEME_DAY_NIGHT = "day/night"
    const val THEME_DAY_BLACK_NIGHT = "day/black"
    @JvmStatic
	fun getTheme(context: Context): String {
        var theme = prefs.get<String>(R.string.key_theme)
        if (THEME_DAY_NIGHT == theme) {
            val currentNightMode =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            theme = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                THEME_DARK
            } else {
                THEME_LIGHT
            }
        } else if (THEME_DAY_BLACK_NIGHT == theme) {
            val currentNightMode =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            theme = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                THEME_BLACK
            } else {
                THEME_LIGHT
            }
        }
        return theme
    }

    @JvmStatic
	fun getThemeRes(context: Context): Int {
        return getThemeRes(context, getTheme(context))
    }

    fun getThemeRes(context: Context?, theme: String): Int {
        return if (context is SubsonicFragmentActivity || context is SettingsActivity) {
            if (prefs.get<Boolean>(R.string.key_color_actionbar)) {
                if (THEME_DARK == theme) {
                    R.style.Theme_DSub_Dark_No_Actionbar
                } else if (THEME_BLACK == theme) {
                    R.style.Theme_DSub_Black_No_Actionbar
                } else {
                    R.style.Theme_DSub_Light_No_Actionbar
                }
            } else {
                if (THEME_DARK == theme) {
                    R.style.Theme_DSub_Dark_No_Color
                } else if (THEME_BLACK == theme) {
                    R.style.Theme_DSub_Black_No_Color
                } else {
                    R.style.Theme_DSub_Light_No_Color
                }
            }
        } else {
            if (THEME_DARK == theme) {
                R.style.Theme_DSub_Dark
            } else if (THEME_BLACK == theme) {
                R.style.Theme_DSub_Black
            } else {
                R.style.Theme_DSub_Light
            }
        }
    }

    fun setTheme(context: Context, theme: String) {
        prefs.set(R.string.key_theme, theme)
    }

    fun applyTheme(context: Context, theme: String) {
        context.setTheme(getThemeRes(context, theme))
        if (prefs.get(R.string.key_override_system_language)) {
            val config = Configuration()
            config.locale = Locale.ENGLISH
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }
}