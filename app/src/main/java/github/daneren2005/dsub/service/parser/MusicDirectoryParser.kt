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
package github.daneren2005.dsub.service.parser

import android.content.Context
import github.daneren2005.dsub.Preferences
import github.daneren2005.dsub.R
import github.daneren2005.dsub.domain.MusicDirectory
import github.daneren2005.dsub.util.ProgressListener
import github.daneren2005.dsub.util.Util.isTagBrowsing
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.xmlpull.v1.XmlPullParser
import java.io.Reader

/**
 * @author Sindre Mehus
 */
class MusicDirectoryParser(context: Context?, instance: Int) :
    MusicDirectoryEntryParser(context, instance), KoinComponent {
    private val prefs: Preferences.App by inject()

    @Throws(Exception::class)
    fun parse(
        artist: String?,
        reader: Reader?,
        progressListener: ProgressListener?
    ): MusicDirectory {
        init(reader)
        val dir = MusicDirectory()
        var eventType: Int
        var isArtist = false
        val checkForDuplicates: Boolean = prefs.get(R.string.key_rename_duplicates)
        val titleMap: MutableMap<String, MusicDirectory.Entry> = HashMap()
        do {
            eventType = nextParseEvent()
            if (eventType == XmlPullParser.START_TAG) {
                val name = elementName
                if ("child" == name || "song" == name || "video" == name) {
                    val entry = parseEntry(artist)
                    entry.grandParent = dir.parent

                    // Only check for songs
                    if (checkForDuplicates && !entry.isDirectory) {
                        // Check if duplicates
                        val disc = if (entry.discNumber != null) Integer.toString(
                            entry.discNumber!!
                        ) else ""
                        val track = if (entry.track != null) Integer.toString(
                            entry.track!!
                        ) else ""
                        val duplicateId = disc + "-" + track + "-" + entry.title
                        val duplicate = titleMap[duplicateId]
                        if (duplicate != null) {
                            // Check if the first already has been rebased or not
                            if (duplicate.title == entry.title) {
                                duplicate.rebaseTitleOffPath()
                            }

                            // Rebase if this is the second instance of this title found
                            entry.rebaseTitleOffPath()
                        } else {
                            titleMap[duplicateId] = entry
                        }
                    }
                    dir.addChild(entry)
                } else if ("directory" == name || "artist" == name || "album" == name && !isArtist) {
                    dir.name = get("name")
                    dir.id = get("id")
                    if (isTagBrowsing(context, instance)) {
                        dir.parent = get("artistId")
                    } else {
                        dir.parent = get("parent")
                    }
                    isArtist = true
                } else if ("album" == name) {
                    val entry = parseEntry(artist)
                    entry.isDirectory = true
                    dir.addChild(entry)
                } else if ("error" == name) {
                    handleError()
                }
            }
        } while (eventType != XmlPullParser.END_DOCUMENT)
        validate()
        return dir
    }

    companion object {
        private val TAG = MusicDirectoryParser::class.java.simpleName
    }
}