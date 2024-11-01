package com.joshlong.podcasts;

import com.joshlong.Podcast;
import com.joshlong.PodcastService;
import com.joshlong.index.IndexingFinishedEvent;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Configuration
class PodcastConfiguration {

	@Bean
	RomePodcastService romePodcastService() {
		return new RomePodcastService();
	}

}

class RomePodcastService implements PodcastService, ApplicationListener<IndexingFinishedEvent> {

	private final URL feedUrl = url(
			"https://studio.media-mogul.io/api/public/feeds/moguls/16386/podcasts/1/episodes.atom");

	private final Object monitor = new Object();

	private final Collection<Podcast> podcasts = new CopyOnWriteArrayList<>();

	private final Logger log = LoggerFactory.getLogger(getClass());

	private static URL url(String u) {
		try {
			return URI.create(u).toURL();
		} //
		catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	private Collection<Podcast> read() throws Exception {
		var list = new ArrayList<Podcast>();
		try {
			// Create SyndFeedInput object to read the feed
			var input = new SyndFeedInput();

			// Read the feed
			var feed = input.build(new XmlReader(feedUrl));

			// Get all entries
			var entries = feed.getEntries();

			// Process each entry
			for (var entry : entries) {
				var podcast = new Podcast(-1, UUID.randomUUID().toString(), entry.getTitle(), //
						entry.getPublishedDate(), //
						// todo figure out how to find the image url
						// todo also can we encode extra data like the uuid and the id as
						// links or something in the link?
						url(entry.getLinks().stream().filter(sl -> true).toList().iterator().next().getHref()), //
						url(""), //
						entry.getDescription().getValue());
				list.add(podcast);
			}
		} //
		catch (Throwable t) {
			log.warn("got an exception trying to read the feed [{}]", this.feedUrl.toExternalForm(), t);
		}
		return list;
	}

	@Override
	public void onApplicationEvent(IndexingFinishedEvent event) {

		synchronized (this.monitor) {
			try {

				this.podcasts.clear();
				var read = read();
				this.podcasts.addAll(read);
			} //
			catch (Exception e) {
				throw new RuntimeException(e);
			}

		}
	}

	@Override
	public Collection<Podcast> getPodcasts() {
		return this.podcasts;
	}

}