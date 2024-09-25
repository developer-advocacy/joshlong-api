package com.joshlong.videos.youtube.jobs;

import com.joshlong.twitter.Twitter;
import com.joshlong.utils.UrlUtils;
import com.joshlong.videos.youtube.client.Video;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class PromotionJob implements Job<Boolean> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final JdbcTemplate jdbcTemplate;

	private final Twitter twitterClient;

	private final String twitterClientId, twitterClientSecret;

	private final String twitterUsername;

	private final String[] playlistIds;

	PromotionJob(JdbcTemplate jdbcTemplate, Twitter twitterClient, String twitterClientId, String twitterClientSecret,
			String twitterUsername, String[] playlistIds) {
		this.jdbcTemplate = jdbcTemplate;
		this.twitterClient = twitterClient;
		this.twitterClientId = twitterClientId;
		this.twitterClientSecret = twitterClientSecret;
		this.twitterUsername = twitterUsername;
		this.playlistIds = playlistIds;
	}

	@Override
	public Boolean run() {
		log.info("=======================================================");
		log.info("PROMOTE");
		log.info("=======================================================");
		promotePlaylist(this.playlistIds[0]);
		return true;
	}

	private void promotePlaylist(String playlistId) {
		log.debug("Going to promote playlist [{}]", playlistId);

		var seed = """
				insert into yt_promotion_batches_entries (batch_id, scheduled, video_id)
				select
				    p.playlist_id  as batch_id,
				    coalesce((  select start_date - interval '1 day' from yt_promotion_batches where
				            start_date < NOW() and stop_date > NOW()
				             and batch_id = p.playlist_id limit 1),
				        (NOW() - interval '1 day') )::date + ((row_number() over (ORDER BY v.rating desc )) || ' day ')::interval as scheduled,
				    v.video_id  as video_id
				from yt_playlists p
				         join yt_playlist_videos pv on p.playlist_id = pv.playlist_id
				         join yt_videos v on pv.video_id = v.video_id
				where p.title ='Spring Tips'
				order by v.rating desc
				on conflict on constraint yt_promotion_batches_entries_batch_id_scheduled_video_id_key
				do update set scheduled = excluded.scheduled
				""";

		var currentBatchUnPromoted = """
				select count(*) as count from yt_promotion_batches b where b.percent_promoted < 100.00 and b.batch_id = ?
				""";

		var todaysEntry = """
				select * from yt_videos v where v.video_id in (
				    select e.video_id from yt_promotion_batches_entries e where e.batch_id = ? and
				        e.promoted is null and NOW()::date = e.scheduled
				)
				""";

		var count = jdbcTemplate.queryForObject(currentBatchUnPromoted, Integer.class, playlistId);
		if (count != null && count == 0) {
			log.info("There are 0 in-flight batch entries");
			jdbcTemplate.update("delete from yt_promotion_batches_entries where batch_id = ?", playlistId);
		}

		log.info("Inserting new entries");
		jdbcTemplate.update(seed);

		var videos = jdbcTemplate.query(todaysEntry, new VideoRowMapper(), playlistId);

		for (Video video : videos) {
			if (tweet(video)) {
				log.info("Marking as tweeted");
				String sql = """
						update yt_promotion_batches_entries
						set promoted = NOW()::date
						where
						    batch_id = ?
						and
						    promoted is null
						and
						    scheduled = NOW()::date
						""";
				jdbcTemplate.update(sql, playlistId);
			}
		}
	}

	private boolean tweet(Video video) {
		var when = Date.from(
				Instant.now().plus(5, TimeUnit.MINUTES.toChronoUnit()).atZone(ZoneId.systemDefault()).toInstant());
		var client = new Twitter.Client(this.twitterClientId, this.twitterClientSecret);
		var text = TweetTextComposer.compose(video.title(), video.videoId());
		return Boolean.TRUE
				.equals(this.twitterClient.scheduleTweet(client, when, this.twitterUsername, text, null).block());
	}

	@SuppressWarnings("unchecked")
	private static List<String> a(Object tags) {
		if (tags instanceof List<?> list) {
			if (!list.isEmpty() && list.get(0) instanceof String) {
				return (List<String>) list;
			}
		}
		else if (tags instanceof String[] stringsArray) {
			return Arrays.asList(stringsArray);
		}
		else if (tags instanceof String string) {
			return List.of(string);
		}
		return List.of();
	}

	private Video videoFor(Map<String, Object> rs) {
		var videoId = (String) rs.get("video_id");
		return new Video(videoId, s(rs.get("title")), s(rs.get("description")),
				Date.from(((LocalDateTime) rs.get("published_at")).atZone(ZoneId.systemDefault()).toInstant()),
				UrlUtils.url(s(rs.get("standard_thumbnail"))), a(rs.get("tags")), i(rs.get("category_id")),
				i(rs.get("view_count")), i(rs.get("like_count")), i(rs.get("favorite_count")),
				i(rs.get("comment_count")), null, false);
	}

	private static String s(Object o) {
		if (o instanceof String str) {
			return str;
		}
		throw new IllegalArgumentException("This isn't a valid String!");
	}

	private static int i(Object o) {
		if (o instanceof Integer integer) {
			return integer;
		}
		if (o instanceof Number number) {
			return number.intValue();
		}
		throw new IllegalArgumentException("This isn't a valid int!");
	}

	private static class VideoRowMapper implements RowMapper<Video> {

		@Override
		public Video mapRow(ResultSet rs, int rowNum) throws SQLException {
			var videoId = rs.getString("video_id");
			var title = rs.getString("title");
			var description = rs.getString("description");
			var publishedAt = rs.getTimestamp("published_at").toLocalDateTime();
			var standardThumbnail = rs.getString("standard_thumbnail");
			var tags = rs.getArray("tags").getArray();
			var categoryId = rs.getInt("category_id");
			var viewCount = rs.getInt("view_count");
			var likeCount = rs.getInt("like_count");
			var favoriteCount = rs.getInt("favorite_count");
			var commentCount = rs.getInt("comment_count");

			try {
				return new Video(videoId, title, description,
						Date.from(publishedAt.atZone(ZoneId.systemDefault()).toInstant()),
						new URI(standardThumbnail).toURL(), a(tags), categoryId, viewCount, likeCount, favoriteCount,
						commentCount, null, false);
			} //
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

	}

}

/**
 * Handles sizing the text of the tweet up to fit into Twitter's 280 char limitations
 */
abstract class TweetTextComposer {

	public static final int MAX_TWEET_LENGTH = 280;

	static String compose(String title, String videoId) {
		var url = "https://www.youtube.com/watch?v=" + videoId;
		var ellipse = "...";
		var full = buildFullTweetText(title, url);
		if (full.length() <= MAX_TWEET_LENGTH) {
			return full;
		}
		int delta = full.length() - MAX_TWEET_LENGTH;
		int desiredWidth = title.length() - delta;
		return buildFullTweetText(rTrimToSpace(title, desiredWidth - ellipse.length()) + ellipse, url);
	}

	private static String buildFullTweetText(String title, String url) {
		return String.format("%s #springboot #rewind %s", title, url);
	}

	private static String rTrimToSpace(String text, int desired) {
		while (text.length() >= desired) {
			int lindx = text.lastIndexOf(' ');
			if (lindx != -1) {
				text = text.substring(0, lindx);
			}
		}
		return text;
	}

}