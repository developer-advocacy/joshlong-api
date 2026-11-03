package com.joshlong.podcasts;

import com.joshlong.index.IndexingFinishedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DomAtomPodcastServiceTest {

	@Test
	void parsesMogulsFeed() throws Exception {
		var service = new DomAtomPodcastService(new ClassPathResource("episodes.atom").getURL());
		service.onApplicationEvent(new IndexingFinishedEvent(Map.of(), new Date()));
		var podcasts = service.getPodcasts().stream().toList();
		assertThat(podcasts).hasSize(2);
		var newest = podcasts.getFirst();
		assertThat(newest.title()).isEqualTo("a newer episode");
		assertThat(newest.id()).isEqualTo(426);
		assertThat(newest.uid()).isEqualTo("00000000-0000-0000-0000-0000000001aa");
		assertThat(newest.date().toInstant()).isEqualTo(Instant.parse("2026-10-29T10:59:11Z"));
		assertThat(newest.episodeUri().toString()).isEqualTo("https://bootifulpodcast.podbean.com/e/newer/");
		assertThat(newest.episodePhotoUri().toString()).isEqualTo("https://example.com/newer.jpg");
		assertThat(podcasts.getLast().id()).isEqualTo(419);
	}

	@Test
	void keepsTheEpisodesItHasWhenTheFeedBreaks() throws Exception {
		var feed = Files.createTempFile("episodes", ".atom");
		try {
			Files.copy(new ClassPathResource("episodes.atom").getInputStream(), feed,
					StandardCopyOption.REPLACE_EXISTING);
			var service = new DomAtomPodcastService(feed.toUri().toURL());
			service.refresh();
			assertThat(service.getPodcasts()).hasSize(2);
			Files.writeString(feed, "<feed");
			service.refresh();
			assertThat(service.getPodcasts()).hasSize(2);
		} //
		finally {
			Files.deleteIfExists(feed);
		}
	}

}
