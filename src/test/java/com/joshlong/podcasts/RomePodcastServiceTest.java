package com.joshlong.podcasts;

import com.joshlong.index.IndexingFinishedEvent;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RomePodcastServiceTest {

	private final DomAtomPodcastService service = new DomAtomPodcastService(
			new URI("https://api.media-mogul.io/public/feeds/moguls/16386/podcasts/1/episodes.atom").toURL());

	RomePodcastServiceTest() throws MalformedURLException, URISyntaxException {
	}

	@Test
	void test() {
		this.service.onApplicationEvent(new IndexingFinishedEvent(Map.of(), new Date()));
		var podcasts = this.service.getPodcasts();
		assertNotNull(podcasts);
		assertFalse(podcasts.isEmpty());

	}

}