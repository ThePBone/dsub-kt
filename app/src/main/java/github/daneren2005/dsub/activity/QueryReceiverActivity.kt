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
import github.daneren2005.dsub.util.Constants
import github.daneren2005.dsub.util.Util

/**
 * Receives search queries and forwards to the SearchFragment.
 *
 * @author Sindre Mehus
 */
class QueryReceiverActivity : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        if (Intent.ACTION_SEARCH == intent.action) {
            doSearch()
        } else if (Intent.ACTION_VIEW == intent.action) {
            showResult(intent.dataString, intent.getStringExtra(SearchManager.EXTRA_DATA_KEY))
        }
        finish()
        Util.disablePendingTransition(this)
    }

    private fun doSearch() {
        val query = intent.getStringExtra(SearchManager.QUERY)
        if (query != null) {
            val intent = Intent(this@QueryReceiverActivity, SubsonicFragmentActivity::class.java)
            intent.putExtra(Constants.INTENT_EXTRA_NAME_QUERY, query)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            Util.startActivityWithoutTransition(this@QueryReceiverActivity, intent)
        }
    }

    private fun showResult(albumId: String?, name: String?) {
        var albumId = albumId
        if (albumId != null) {
            val intent = Intent(this, SubsonicFragmentActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.putExtra(Constants.INTENT_EXTRA_VIEW_ALBUM, true)
            if (albumId.indexOf("dsub-ar-") == 0) {
                intent.putExtra(Constants.INTENT_EXTRA_NAME_ARTIST, true)
                albumId = albumId.replace("dsub-ar-", "")
            } else if (albumId.indexOf("dsub-so-") == 0) {
                intent.putExtra(Constants.INTENT_EXTRA_SEARCH_SONG, name)
                albumId = albumId.replace("dsub-so-", "")
            }
            intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, albumId)
            if (name != null) {
                intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, name)
            }
            Util.startActivityWithoutTransition(this, intent)
        }
    }

    companion object {
        private val TAG = QueryReceiverActivity::class.java.simpleName
    }
}