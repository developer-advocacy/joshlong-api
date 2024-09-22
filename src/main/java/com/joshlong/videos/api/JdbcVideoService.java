package com.joshlong.videos.api;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URL;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
class JdbcVideoService implements VideoService {

	private final JdbcTemplate jdbcTemplate;

	private final Function<Map<String, Object>, Channel> channelMapper = row -> new Channel(
			(String) row.get("channel_id"));

	private final Function<Map<String, Object>, Playlist> playlistMapper = row -> new Playlist(
			(String) row.get("playlist_id"), (String) row.get("title"));

	private final Function<Map<String, Object>, Video> videoMapper = row -> new Video((String) row.get("video_id"),
			(String) row.get("title"), url((String) row.get("standard_thumbnail")), (String) row.get("description"),
			instant((LocalDateTime) row.get("published_at")), (int) row.get("view_count"),
			(int) row.get("favorite_count"), (int) row.get("comment_count"), (int) row.get("like_count"),
			(String[]) row.get("tags"));

	JdbcVideoService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<Playlist> playlistsByName(String name) {
		String sql = "select * from yt_playlists where title ilike ?";
		return this.jdbcTemplate.query(sql,
				(rs, rowNum) -> new Playlist(rs.getString("playlist_id"), rs.getString("title")), "%" + name + "%");
	}

	@Override
	public List<Video> videosByChannel(Channel channel) {
		String sql = """
				select * from
				    yt_videos v,
				    yt_channel_videos c
				where
				    v.video_id =  c.video_id  and
				    c.channel_id = ?
				order by
				    v.published_at desc
				""";
		return this.jdbcTemplate.query(sql, new VideoRowMapper(), channel.id());
	}

	@Override
	public Playlist playlistById(String id) {
		String sql = "select * from yt_playlists where playlist_id = ?";
		return this.jdbcTemplate.queryForObject(sql,
				(rs, rowNum) -> new Playlist(rs.getString("playlist_id"), rs.getString("title")), id);
	}

	@Override
	public List<Video> videosByPlaylist(Playlist playlist) {
		String sql = """
				select v.* from yt_videos v, yt_playlists p, yt_playlist_videos pv
				    where
				        pv.playlist_id = p.playlist_id
				    and
				        v.video_id = pv.video_id
				    and
				        p.playlist_id = ?
				order by
				    v.published_at desc
				""";
		return this.jdbcTemplate.query(sql, new VideoRowMapper(), playlist.id());
	}

	@Override
	public Channel channelById(String id) {
		String sql = "select * from yt_channels where channel_id = ?";
		var res = this.jdbcTemplate.query(sql, (rs, rowNum) -> new Channel(rs.getString("channel_id")), id);
		return res.isEmpty() ? null : res.getFirst();
	}

	@Override
	public List<Channel> channels() {
		String sql = "select * from yt_channels";
		return this.jdbcTemplate.query(sql, (rs, rowNum) -> new Channel(rs.getString("channel_id")));
	}

	@SneakyThrows
	private static URL url(String url) {
		return URI.create(url).toURL();
	}

	private static Instant instant(LocalDateTime localDateTime) {
		return localDateTime.toInstant(ZoneOffset.UTC);
	}

	private static class VideoRowMapper implements RowMapper<Video> {

		@Override
		public Video mapRow(ResultSet rs, int rowNum) {
			try {
				return new Video(rs.getString("video_id"), rs.getString("title"),
						URI.create(rs.getString("standard_thumbnail")).toURL(), rs.getString("description"),
						rs.getTimestamp("published_at").toInstant(), rs.getInt("view_count"),
						rs.getInt("favorite_count"), rs.getInt("comment_count"), rs.getInt("like_count"),
						(String[]) rs.getArray("tags").getArray());
			} //
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

	}

}

record Playlist(String id, String title) {
}

record Channel(String id) {
}

record Video(String id, String title, URL thumbnail, String description, Instant published, int views, int favorites,
		int comments, int likes, String[] tags) {
}