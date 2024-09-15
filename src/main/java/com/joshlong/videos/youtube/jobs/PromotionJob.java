package com.joshlong.videos.youtube.jobs;

//import com.joshlong.twitter.Twitter;
import com.joshlong.videos.youtube.client.Video;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
class PromotionJob implements ReactiveJob<Boolean> {

	private final DatabaseClient db;

	// private final Twitter twitterClient;

	private final String twitterClientId, twitterClientSecret;

	private final String twitterUsername;

	private final String[] playlistIds;

	@Override
	public Publisher<Boolean> run() {
		log.info("=======================================================");
		log.info("PROMOTE");
		log.info("=======================================================");
		return this.promotePlaylist(this.playlistIds[0]);
	}

	private Mono<Boolean> promotePlaylist(String playlistId) {
		log.debug("going to promote playlist [" + playlistId + "]");
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
				select count(*) as count from yt_promotion_batches b where b.percent_promoted < 100.00 and b.batch_id = :batchId
				""";
		var todaysEntry = """
				select * from yt_videos v where v.video_id in (
				    select e.video_id from yt_promotion_batches_entries e where e.batch_id = :batchId and
				        e.promoted is null and NOW()::date = e.scheduled
				)
				""";
		return this.db//
				.sql(currentBatchUnPromoted)//
				.bind("batchId", playlistId)//
				.fetch()//
				.all()//
				.doOnNext(rec -> log.info("got count: " + rec)).flatMap(record -> {
					var c = ((Number) record.get("count")).intValue();
					if (c == 0) {
						log.info("there are 0 in-flight batch entries");
						return this.db//
								.sql("delete from yt_promotion_batches_entries where batch_id = :batchId")
								.bind("batchId", playlistId)//
								.fetch()//
								.rowsUpdated();
					} //
					else {
						log.info("returning empty");
						return Mono.empty();
					}
				})//
				.doOnNext(c -> log.info("the count is " + c))//
				.thenMany(this.db//
						.sql(seed)//
						.fetch()//
						.rowsUpdated()//
						.doOnNext(c -> log.info("ran the seed query"))) //
				.then(this.db//
						.sql(todaysEntry)//
						.bind("batchId", playlistId)//
						.fetch()//
						.all()//
						.doOnNext(r -> log.info("today's entry? " + r.toString()))//
						.map(this::videoFor)//
						.doOnNext(v -> log.info("got the video " + v.videoId()))//
						.flatMap(this::tweet)//
						.doOnNext(t -> log.info("tweeted? " + t))//
						.flatMap(tweeted -> {
							log.info("going to mark as tweeted");
							var sql = """
									   update yt_promotion_batches_entries
									   set promoted= NOW()::date
									   where
									       batch_id = :batchId
									   and
									       promoted is null
									   and
									       scheduled = NOW()::date
									""";
							if (tweeted)
								return this.db.sql(sql)//
										.bind("batchId", playlistId)//
										.fetch()//
										.rowsUpdated()//
										.map(updated -> updated > 0)//
										.doOnNext(t -> log.info("marked as tweeted"));//
							log.info("no tweet, therefore false");
							return Mono.just(false);

						}) //
						.singleOrEmpty());//

	}

	@SneakyThrows
	private Mono<Boolean> tweet(Video video) {
		var when = Date.from(
				Instant.now().plus(5, TimeUnit.MINUTES.toChronoUnit()).atZone(ZoneId.systemDefault()).toInstant());
		// var client = new Twitter.Client(this.twitterClientId,
		// this.twitterClientSecret);
		// var text = TweetTextComposer.compose(video.title(), video.videoId());
		// return this.twitterClient.scheduleTweet(client, when, this.twitterUsername,
		// text, null);
		return Mono.just(true);
	}

	private static List<String> a(Object tags) {
		if (tags instanceof List list)
			if (!list.isEmpty())
				if (list.getFirst() instanceof String)
					return (List<String>) list;

		if (tags instanceof String[] stringsArray)
			return Arrays.asList(stringsArray);

		if (tags instanceof String string)
			return List.of(string);

		return List.of();
	}

	@SneakyThrows
	private Video videoFor(Map<String, Object> rs) {
		var videoId = (String) rs.get("video_id");
		return new Video(videoId, s(rs.get("title")), s(rs.get("description")),
				Date.from(((LocalDateTime) rs.get("published_at")).atZone(ZoneId.systemDefault()).toInstant()),
				new URI(s(rs.get("standard_thumbnail"))).toURL(), a(rs.get("tags")), i(rs.get("category_id")),
				i(rs.get("view_count")), i(rs.get("like_count")), i(rs.get("favorite_count")),
				i(rs.get("comment_count")), null, false);
	}

	private static String s(Object o) {
		if (o instanceof String str)
			return str;
		throw new IllegalArgumentException("this isn't a valid String!");
	}

	private static int i(Object o) {
		if (o instanceof Integer integer)
			return integer;
		if (o instanceof Number number)
			return number.intValue();
		throw new IllegalArgumentException("this isn't a valid int!");
	}

}

/**
 * Handles sizing the text of the tweet up to fit in to Twitter's 280 char limitations
 */
@Slf4j
abstract class TweetTextComposer {

	public static final int MAX_TWEET_LENGTH = 280;

	static String compose(String title, String videoId) {
		var url = "https://www.youtube.com/watch?v=" + videoId;
		var ellipse = "...";
		var full = buildFullTweetText(title, url);
		if (full.length() <= MAX_TWEET_LENGTH)
			return full;
		var delta = full.length() - MAX_TWEET_LENGTH;
		var desiredWidth = title.length() - delta;
		return buildFullTweetText(rTrimToSpace(title, desiredWidth - ellipse.length()) + ellipse, url);
	}

	private static String buildFullTweetText(String title, String url) {
		return String.format("%s #springboot #rewind %s", title, url);
	}

	private static String rTrimToSpace(String text, int desired) {
		while (text.length() >= desired) {
			var lindx = text.lastIndexOf(' ');
			if (lindx != -1)
				text = text.substring(0, lindx);
		}
		return text;
	}

}