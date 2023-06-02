package com.joshlong.videos.api;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Function;

/**
 * Represents the view of the data from the SQL database. This is ideal for searching, not
 * to mention that the data is cached, and therefore faster to retrieve. The only
 * drawback, of course, is that the data is somewhat stale.
 *
 * @author Josh Long
 */
@Slf4j
@Service
@Transactional(readOnly = true)
class R2dbcVideoService implements VideoService {

	private final DatabaseClient databaseClient;

	private final Function<Map<String, Object>, Channel> channelMapper = requests -> new Channel(
			(String) requests.get("channel_id"));

	private final Function<Map<String, Object>, Playlist> playlistMapper = row -> new Playlist(
			(String) row.get("playlist_id"), (String) row.get("title"));

	private final Function<Map<String, Object>, Video> videoMapper = request -> new Video(
			(String) request.get("video_id"), (String) request.get("title"),
			url((String) request.get("standard_thumbnail")), (String) request.get("description"),
			instant((LocalDateTime) request.get("published_at")), (int) request.get("view_count"),
			(int) request.get("favorite_count"), (int) request.get("comment_count"), (int) request.get("like_count"),
			(String[]) request.get("tags"));

	R2dbcVideoService(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	@Override
	public Flux<Playlist> playlistsByName(String name) {
		return this.databaseClient//
				.sql("select * from yt_playlists where title ilike :name  ")//
				.bind("name", "%" + name + "%")//
				.fetch()//
				.all()//
				.map(this.playlistMapper);
	}

	@Override
	public Flux<Video> videosByChannel(Channel channel) {
		return this.databaseClient//
				.sql("""
							select * from
						        yt_videos v,
						        yt_channel_videos c
						    where
						        v.video_id =  c.video_id  and
						        c.channel_id = :cid
							order by
								v.published_at desc
						""")//
				.bind("cid", channel.id())//
				.fetch()//
				.all()//
				.map(this.videoMapper);
	}

	@Override
	public Mono<Playlist> playlistById(String id) {
		return this.databaseClient//
				.sql("select * from yt_playlists where playlist_id = :pid")//
				.bind("pid", id)//
				.fetch()//
				.all()//
				.map(this.playlistMapper)//
				.singleOrEmpty();
	}

	@Override
	public Flux<Video> videosByPlaylist(Playlist playlist) {
		return this.databaseClient//
				.sql("""
						select v.* from yt_videos v, yt_playlists  p, yt_playlist_videos pv
						    where
						pv.playlist_id = p.playlist_id
						    and
						v.video_id = pv.video_id
						    and
						p.playlist_id = :pid
							order by
						v.published_at desc
						""")//
				.bind("pid", playlist.id())//
				.fetch()//
				.all()//
				.map(this.videoMapper);
	}

	@Override
	public Mono<Channel> channelById(String id) {
		return this.databaseClient//
				.sql("select  * from yt_channels where channel_id = :cid")//
				.bind("cid", id)//
				.fetch()//
				.all()//
				.singleOrEmpty()//
				.map(this.channelMapper);
	}

	@Override
	public Flux<Channel> channels() {
		return this.databaseClient//
				.sql("select * from yt_channels")//
				.fetch()//
				.all()//
				.map(this.channelMapper);
	}

	@SneakyThrows
	private static URL url(String url) {
		return new URL(url);
	}

	private static Instant instant(LocalDateTime localDateTime) {
		return localDateTime.toInstant(ZoneOffset.UTC);
	}

}

record Playlist(String id, String title) {
}

record Channel(String id) {
}

record Video(String id, String title, URL thumbnail, String description, Instant published, int views, int favorites,
		int comments, int likes, String[] tags) {
}