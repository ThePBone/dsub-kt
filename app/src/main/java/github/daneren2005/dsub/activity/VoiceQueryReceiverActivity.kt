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

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import github.daneren2005.dsub.util.Constants
import github.daneren2005.dsub.util.Util

/**
 * Receives voice search queries and forwards to the SearchFragment.
 *
 * http://android-developers.blogspot.com/2010/09/supporting-new-music-voice-action.html
 *
 * @author Sindre Mehus
 */
class VoiceQueryReceiverActivity : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(SearchManager.QUERY)?.let { query ->
            Intent(this@VoiceQueryReceiverActivity, SubsonicFragmentActivity::class.java).apply {
                putExtra(Constants.INTENT_EXTRA_NAME_QUERY, query)

                if (GMS_SEARCH_ACTION != intent.action) {
                    putExtra(Constants.INTENT_EXTRA_NAME_AUTOPLAY, true)
                }

                intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)?.let {
                    putExtra(MediaStore.EXTRA_MEDIA_ARTIST, it)
                }
                intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)?.let {
                    putExtra(MediaStore.EXTRA_MEDIA_ALBUM, it)
                }
                intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)?.let {
                    putExtra(MediaStore.EXTRA_MEDIA_TITLE, it)
                }
                putExtra(
                    MediaStore.EXTRA_MEDIA_FOCUS,
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
                )
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }.also {
                Util.startActivityWithoutTransition(this@VoiceQueryReceiverActivity, it)
            }
        }

        finish()
        Util.disablePendingTransition(this)
    }

    companion object {
        private const val GMS_SEARCH_ACTION = "com.google.android.gms.actions.SEARCH_ACTION"
    }
}