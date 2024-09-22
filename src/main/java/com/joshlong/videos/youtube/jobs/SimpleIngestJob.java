package com.joshlong.videos.youtube.jobs;

import com.joshlong.videos.youtube.client.Channel;
import com.joshlong.videos.youtube.client.Playlist;
import com.joshlong.videos.youtube.client.Video;
import com.joshlong.videos.youtube.client.YoutubeClient;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.Collection;

@Slf4j
@RequiredArgsConstructor
class SimpleIngestJob implements IngestJob {

	private final YoutubeClient client;

	private final JdbcClient db;

	private final String channelId;

	@Override
	@SneakyThrows
	public Collection<Playlist> run() {
		log.info("=======================================================");
		log.info("INGEST (" + this.channelId + ")");
		log.info("=======================================================");
		// 0. reset all the flush states
		// 1. get all the playlists for the main channel
		// 2. for each playlist's videos, write the (video and playlist) into its join
		// table and the (video and channel) into its join table
		// 3. get all the videos for the channel, write them, also noting the channel and
		// video in the correct join table
		// 4. for each unique playlist_id in yt_playlist_videos, get the playlist data
		// from the API and write it to yt_playlists
		// 5. for each unique channel in yt_channel_videos, get the channel data from the
		// API and write it to yt_channels
		var allPlaylists = new ArrayList<Playlist>();
		this.resetTablesFreshStatus();
		var channel = this.client.getChannelById(this.channelId);
		this.client.getAllVideosByChannel(channel.channelId()).stream().filter(v -> !v.upcoming())
				.forEach(this::doWriteVideo);
		var playlistsByChannel = this.client.getAllPlaylistsByChannel(channel.channelId());
		for (var playlist : playlistsByChannel) {
			allPlaylists.add(playlist);
			var videos = this.client.getAllVideosByChannel(playlist.playlistId());
			for (var video : videos) {
				this.doWritePlaylistsVideos(channel, playlist, video);
			}
		}
		this.enrichChannels();
		this.enrichPlaylists();
		return allPlaylists;
	}

	private void enrichChannels() {
		var list = this.db//
				.sql(" select distinct channel_id from yt_channel_videos ")//
				.query((r, i) -> r.getString("channel_id")).list();//
		var newList = new ArrayList<Channel>();
		for (var s : list) {
			var c = client.getChannelById(s);
			this.doWriteChannel(c);
			newList.add(c);
		}
	}

	private void enrichPlaylists() {
		var list = this.db.sql(" select distinct playlist_id from yt_playlist_videos pv ")//
				.query((rs, rowNum) -> rs.getString("playlist_id")).single();//
		var playlist = this.client.getPlaylistById(list);
		this.doWritePlaylist(playlist);
	}

	private void doWritePlaylistsVideos(Channel channel, Playlist playlist, Video video) {
		this.db.sql("""
				insert into yt_playlist_videos(
				    video_id,
				    playlist_id
				)
				values( :videoId, :playlistId )
				on conflict on constraint yt_playlist_videos_pkey
				do nothing
				""")//
				.param("videoId", video.videoId())//
				.param("playlistId", playlist.playlistId())//
				.update();

		this.doWriteVideo(video);

	}

	private void doWritePlaylist(Playlist playlist) {
		var sql = """
				insert into yt_playlists (
				    playlist_id,
				    channel_id,
				    published_at,
				    title,
				    description,
				    item_count,
				    fresh
				)
				values( :playlistId, :channelId, :publishedAt, :title, :description, :itemCount, true )
				on conflict on constraint yt_playlists_pkey
				do update SET fresh = true where yt_playlists.playlist_id = :playlistId
				""";
		this.db.sql(sql)//
				.param("itemCount", playlist.itemCount())//
				.param("description", playlist.description())//
				.param("title", playlist.title())//
				.param("publishedAt", playlist.publishedAt())//
				.param("channelId", playlist.channelId())//
				.param("playlistId", playlist.playlistId())//
				.update();
	}

	private void doWriteVideo(Video video) {

		if (log.isDebugEnabled())
			log.debug("video ({}) ({}) {} ", video.channelId(), video.videoId(), video.title());

		this.db//
				.sql("""
						insert into yt_channel_videos(video_id, channel_id)
						values(:vid, :cid)
						on conflict on constraint yt_channel_videos_pkey
						do nothing
								""")//
				.param("vid", video.videoId())//
				.param("cid", video.channelId())//
				.update();

		this.db//
				.sql("""
						  insert into yt_videos (
							video_id ,
							title,
							description,
							published_at ,
							standard_thumbnail,
							category_id,
							view_count,
							favorite_count,
							comment_count  ,
							like_count ,
							fresh,
							tags
						  )
						  values (
						   :videoId,  :title,  :description, :publishedAt,
						   :standardThumbnail,  :categoryId,  :viewCount,
						   :favoriteCount, :commentCount, :likeCount , true,   :tags
						  )
						  on conflict on CONSTRAINT yt_videos_pkey
						  do update set
						   fresh = true,
						   video_id  = excluded.video_id,
						   title = excluded.title,
						   description = excluded.description,
						   published_at  = excluded.published_at,
						   standard_thumbnail = excluded.standard_thumbnail,
						   category_id = excluded.category_id,
						   view_count = excluded.view_count,
						   favorite_count = excluded.favorite_count,
						   comment_count  = excluded.comment_count,
						   like_count =  excluded.like_count ,
						   tags = excluded.tags
						where
						   yt_videos.video_id =  :videoId
						""")//
				.param("videoId", video.videoId())//
				.param("title", video.title()) //
				.param("description", video.description())//
				.param("publishedAt", video.publishedAt())//
				.param("standardThumbnail", video.standardThumbnail().toExternalForm())//
				.param("categoryId", video.categoryId())//
				.param("viewCount", video.viewCount())//
				.param("favoriteCount", video.favoriteCount())//
				.param("commentCount", video.commentCount())//
				.param("tags", video.tags().toArray(new String[0]))//
				.param("likeCount", video.likeCount())//
				.update();

	}

	@SneakyThrows
	private void doWriteChannel(Channel channel) {
		var sql = """
				    insert into yt_channels(channel_id, description, published_at, title, fresh)
				    values (  :channelId , :description, :publishedAt, :title, true)
				    on conflict on constraint yt_channels_pkey
				    do update SET fresh = true where yt_channels.channel_id = :channelId
				""";
		this.db.sql(sql)//
				.param("channelId", channel.channelId())//
				.param("description", channel.description())//
				.param("publishedAt", channel.publishedAt())//
				.param("title", channel.title())//
				.update();

	}

	private void resetTablesFreshStatus() {
		var tables = "yt_playlist_videos,yt_channels,yt_playlists,yt_videos".split(",");
		for (var table : tables)
			this.db.sql("update " + table + " set fresh = false").update();
	}

}
