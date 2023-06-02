package com.joshlong.videos.youtube.jobs;

import com.joshlong.videos.youtube.client.Channel;
import com.joshlong.videos.youtube.client.Playlist;
import com.joshlong.videos.youtube.client.Video;
import com.joshlong.videos.youtube.client.YoutubeClient;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
class SimpleIngestJob implements IngestJob {

	private final YoutubeClient client;

	private final DatabaseClient databaseClient;

	private final String channelId;

	@Override
	@SneakyThrows
	public Flux<Playlist> run() {
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
		var youtubeIngestPipeline = this.resetTablesFreshStatus()//
				.then(client.getChannelById(this.channelId))//
				.flatMapMany(channel -> {//
					var allVideos = client.getAllVideosByChannel(channel.channelId())//
							.filter(v -> !v.upcoming()) // we do NOT want things with zero
							// views from the future in the
							// mix! division by zero is BAD
							.flatMap(this::doWriteVideo);
					var playlistVideos = client.getAllPlaylistsByChannel(channel.channelId())//
							.flatMap(playlist -> client.getAllVideosByPlaylist(playlist.playlistId())
									.flatMap(video -> doWritePlaylistsVideos(channel, playlist, video)));
					return playlistVideos.thenMany(allVideos);
				})//
				.thenMany(this.enrichChannels())//
				.thenMany(this.enrichPlaylists());//
		return youtubeIngestPipeline//
				.doOnSubscribe(sub -> log.info("starting the Youtube Ingest process"));

	}

	private Flux<Channel> enrichChannels() {
		return this.databaseClient//
				.sql(" select distinct channel_id from yt_channel_videos ")//
				.fetch()//
				.all()//
				.map(r -> (String) r.get("channel_id"))//
				.flatMap(client::getChannelById)//
				.flatMap(this::doWriteChannel);
	}

	private Flux<Playlist> enrichPlaylists() {
		// 4. for each unique playlist_id in yt_playlist_videos, get the playlist data
		// from the API and write it to yt_playlists todo
		return this.databaseClient.sql("""
				select distinct playlist_id from yt_playlist_videos pv
				""")//
				.fetch()//
				.all()//
				.map(record -> (String) record.get("playlist_id"))//
				.flatMap(pid -> this.client//
						.getPlaylistById(pid)//
						.flatMapMany(this::doWritePlaylist)//
				);

	}

	private Mono<Playlist> doWritePlaylistsVideos(Channel channel, Playlist playlist, Video video) {
		var writePlaylistVideo = this.databaseClient.sql("""
				insert into yt_playlist_videos(
				    video_id,
				    playlist_id
				)
				values( :videoId, :playlistId )
				on conflict on constraint yt_playlist_videos_pkey
				do nothing
				""")//
				.bind("videoId", video.videoId())//
				.bind("playlistId", playlist.playlistId())//
				.fetch()//
				.rowsUpdated();
		return writePlaylistVideo.then(doWriteVideo(video)).then(Mono.just(playlist));
	}

	private Mono<Playlist> doWritePlaylist(Playlist playlist) {
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
		return this.databaseClient.sql(sql)//
				.bind("itemCount", playlist.itemCount())//
				.bind("description", playlist.description())//
				.bind("title", playlist.title())//
				.bind("publishedAt", playlist.publishedAt())//
				.bind("channelId", playlist.channelId())//
				.bind("playlistId", playlist.playlistId())//
				.fetch()//
				.rowsUpdated()//
				.map(count -> playlist);
	}

	private Mono<Video> doWriteVideo(Video video) {
		if (log.isDebugEnabled())
			log.debug("video (" + video.channelId() + ") (" + video.videoId() + ") " + video.title() + " ");

		var writeChannelVideo = this.databaseClient.sql("""
				insert into yt_channel_videos(video_id, channel_id)
				values(:vid, :cid)
				on conflict on constraint yt_channel_videos_pkey
				do nothing
				""").bind("vid", video.videoId())//
				.bind("cid", video.channelId())//
				.fetch()//
				.rowsUpdated();

		var videoInsert = this.databaseClient//
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
				.bind("videoId", video.videoId())//
				.bind("title", video.title()) //
				.bind("description", video.description())//
				.bind("publishedAt", video.publishedAt())//
				.bind("standardThumbnail", video.standardThumbnail().toExternalForm())//
				.bind("categoryId", video.categoryId())//
				.bind("viewCount", video.viewCount())//
				.bind("favoriteCount", video.favoriteCount())//
				.bind("commentCount", video.commentCount())//
				.bind("tags", video.tags().toArray(new String[0]))//
				.bind("likeCount", video.likeCount())//
				.fetch()//
				.rowsUpdated()//
				.map(count -> video);

		return writeChannelVideo.then(videoInsert);
	}

	@SneakyThrows
	private Mono<Channel> doWriteChannel(Channel channel) {
		var sql = """
				    insert into yt_channels(channel_id, description, published_at, title, fresh)
				    values (  :channelId , :description, :publishedAt, :title, true)
				    on conflict on constraint yt_channels_pkey
				    do update SET fresh = true where yt_channels.channel_id = :channelId
				""";
		return this.databaseClient.sql(sql)//
				.bind("channelId", channel.channelId())//
				.bind("description", channel.description())//
				.bind("publishedAt", channel.publishedAt())//
				.bind("title", channel.title())//
				.fetch()//
				.rowsUpdated()//
				.thenReturn(channel);
	}

	private Flux<Long> resetTablesFreshStatus() {
		return Flux//
				.fromArray("yt_playlist_videos,yt_channels,yt_playlists,yt_videos".split(","))//
				.flatMap(table -> databaseClient//
						.sql("update " + table + " set fresh = false")//
						.fetch()//
						.rowsUpdated()//
				);
	}

}
