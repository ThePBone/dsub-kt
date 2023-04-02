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
package github.daneren2005.dsub.service.parser;

import android.content.Context;

import github.daneren2005.dsub.domain.Bookmark;
import github.daneren2005.dsub.domain.MusicDirectory;

/**
 * @author Sindre Mehus
 */
public class MusicDirectoryEntryParser extends AbstractParser {
    public MusicDirectoryEntryParser(Context context, int instance) {
        super(context, instance);
    }

    protected MusicDirectory.Entry parseEntry(String artist) {
        MusicDirectory.Entry entry = new MusicDirectory.Entry();
        entry.id = get("id");
		entry.parent = get("parent");
		entry.artistId = get("artistId");
        entry.title = get("title");
		if(entry.title == null) {
			entry.title = get("name");
		}
        entry.setDirectory(getBoolean("isDir"));
        entry.coverArt = get("coverArt");
        entry.artist = get("artist");
        entry.setStarred(get("starred") != null);
        entry.year = getInteger("year");
        entry.genre = get("genre");
		entry.album = get("album");
		entry.setRating(getInteger("userRating"));

        if (!entry.isDirectory()) {
			entry.albumId = get("albumId");
            entry.track = getInteger("track");
            entry.contentType = get("contentType");
            entry.suffix = get("suffix");
            entry.transcodedContentType = get("transcodedContentType");
            entry.transcodedSuffix = get("transcodedSuffix");
            entry.size = getLong("size");
            entry.duration = getInteger("duration");
            entry.bitRate = getInteger("bitRate");
            entry.path = get("path");
            entry.setVideo(getBoolean("isVideo"));
			entry.discNumber = getInteger("discNumber");

			Integer bookmark = getInteger("bookmarkPosition");
			if(bookmark != null) {
				entry.bookmark = new Bookmark(bookmark);
			}

			String type = get("type");
			if("podcast".equals(type)) {
				entry.type = MusicDirectory.Entry.TYPE_PODCAST;
			} else if("audiobook".equals(type) || (entry.genre != null && "audiobook".equals(entry.genre.toLowerCase()))) {
				entry.type = MusicDirectory.Entry.TYPE_AUDIO_BOOK;
			}
        } else if(!"".equals(artist)) {
			entry.path = artist + "/" + entry.title;
		}
        return entry;
    }
	
	protected MusicDirectory.Entry parseArtist() {
		MusicDirectory.Entry entry = new MusicDirectory.Entry();
		
		entry.id = get("id");
		entry.title = get("name");
		entry.path = entry.title;
		entry.setStarred(true);
		entry.setDirectory(true);
		
		return entry;
	}
}
