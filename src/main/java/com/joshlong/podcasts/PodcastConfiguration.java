package com.joshlong.podcasts;

import com.joshlong.Podcast;
import com.joshlong.PodcastService;
import com.joshlong.index.IndexingFinishedEvent;
import com.rometools.rome.feed.synd.SyndLink;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Configuration
class PodcastConfiguration {

	@Bean
	RomePodcastService romePodcastService() {
		return new RomePodcastService();
	}

}

class RomePodcastService implements PodcastService, ApplicationListener<IndexingFinishedEvent> {

	private final URL feedUrl = url("https://api.media-mogul.io/public/feeds/moguls/16386/podcasts/1/episodes.atom");

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

	private static String urlForLink(List<SyndLink> links, String rel) {
		return Objects.requireNonNull(links
						.stream()
						.filter(sl -> sl.getRel().equals(rel))
						.findFirst()
						.orElse(null))
				.getHref();
	}
	
	private Collection<Podcast> read() throws Exception {
		var list = new ArrayList<Podcast>();
		try (var feedUrl = this.feedUrl.openStream()) {
			// Create SyndFeedInput object to read the feed
			var input = new SyndFeedInput();

			// Read the feed
			var feed = input.build(new XmlReader(feedUrl));

			// Get all entries
			var entries = feed.getEntries();

			// Process each entry
			for (var entry : entries) {
				var imgUrl = urlForLink(entry.getLinks(), "enclosure");
				var podcastUrl = urlForLink(entry.getLinks(), "alternate");
				Assert.notNull(podcastUrl, "there must be a valid link to the podcast episode");
				Assert.notNull(imgUrl, "there must be a valid link to the podcast episode image");
				var podcast = new Podcast(
						-1,
						UUID.randomUUID().toString(),
						entry.getTitle(), //
						entry.getPublishedDate(), //
						url(imgUrl),
						url(podcastUrl),
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