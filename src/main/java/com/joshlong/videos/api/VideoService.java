package com.joshlong.videos.api;

import java.util.List;

/**
 * A service for working with the database.
 *
 * @author Josh Long
 */
interface VideoService {
	
	List<Playlist> playlistsByName(String name);

	List<Video> videosByChannel(Channel channel);

	Playlist playlistById(String id);

	List<Video> videosByPlaylist(Playlist playlist);

	Channel channelById(String id);

	List<Channel> channels();

}
