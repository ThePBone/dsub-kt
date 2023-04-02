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
import android.util.Log;

import github.daneren2005.dsub.R;
import github.daneren2005.dsub.domain.Bookmark;
import github.daneren2005.dsub.domain.MusicDirectory;
import github.daneren2005.dsub.domain.PodcastEpisode;
import github.daneren2005.dsub.util.FileUtil;
import github.daneren2005.dsub.util.ProgressListener;
import java.io.Reader;
import org.xmlpull.v1.XmlPullParser;

/**
 *
 * @author Scott
 */
public class PodcastEntryParser extends AbstractParser {
	private static int bogusId = -1;
	
	public PodcastEntryParser(Context context, int instance) {
		super(context, instance);
	}
	
	public MusicDirectory parse(String channel, Reader reader, ProgressListener progressListener) throws Exception {
		init(reader);

		MusicDirectory episodes = new MusicDirectory();
		String coverArt = null;
		int eventType;
		boolean valid = false;
		do {
			eventType = nextParseEvent();
			if (eventType == XmlPullParser.START_TAG) {
				String name = getElementName();
				if ("channel".equals(name)) {
					String id = get("id");
					if(id.equals(channel)) {
						episodes.id = id;
						episodes.name = get("title");
						coverArt = get("coverArt");
						valid = true;
					} else {
						valid = false;
					}
				} else if("newestPodcasts".equals(name)) {
					valid = true;
				} else if ("episode".equals(name) && valid) {
					PodcastEpisode episode = new PodcastEpisode();
					episode.setEpisodeId(get("id"));
					episode.id = get("streamId");
					episode.title = get("title");
					if(episodes.id != null) {
						episode.artist = episodes.name;
						episode.parent = episodes.id;
					} else {
						episode.parent = get("channelId");
					}
					episode.album = get("description");
					episode.setDate(get("publishDate"));
					if(episode.getDate() == null) {
						episode.setDate(get("created"));
					}
					if(episode.getDate() != null && episode.getDate().indexOf("T") != -1) {
						episode.setDate(episode.getDate().replace("T", " "));
					}
					episode.setStatus(get("status"));
					if(coverArt == null) {
						episode.coverArt = get("coverArt");
					} else {
						episode.coverArt = coverArt;
					}
					episode.size = getLong("size");
					episode.contentType = get("contentType");
					episode.suffix = get("suffix");
					episode.duration = getInteger("duration");
					episode.bitRate = getInteger("bitRate");
					episode.setVideo(getBoolean("isVideo"));
					episode.path = get("path");
					if(episode.path == null) {
						episode.path = FileUtil.getPodcastPath(context, episode);
					} else if(episode.path.indexOf("Podcasts/") == 0) {
						episode.path = episode.path.substring("Podcasts/".length());
					}

					Integer bookmark = getInteger("bookmarkPosition");
					if(bookmark != null) {
						episode.bookmark = new Bookmark(bookmark);
					}
					episode.type = MusicDirectory.Entry.TYPE_PODCAST;
					
					if(episode.id == null) {
						episode.id = String.valueOf(bogusId);
						bogusId--;
					}
					episodes.addChild(episode);
				} else if ("error".equals(name)) {
					handleError();
				}
			}
		} while (eventType != XmlPullParser.END_DOCUMENT);

		validate();
		return episodes;
	}
}
