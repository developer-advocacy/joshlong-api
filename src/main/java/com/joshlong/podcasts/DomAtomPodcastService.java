package com.joshlong.podcasts;

import com.joshlong.Podcast;
import com.joshlong.PodcastService;
import com.joshlong.index.IndexingFinishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * this uses the RSS feed
 */
class DomAtomPodcastService implements PodcastService, ApplicationListener<IndexingFinishedEvent> {

	private static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Object monitor = new Object();

	private final Collection<Podcast> podcasts = new CopyOnWriteArrayList<>();

	private final URL feedUrl;

	private final Comparator<Podcast> comparator = ((Comparator<Podcast>) (o1, o2) -> {

		if (o1.date() != null && o2.date() != null)
			return o1.date().compareTo(o2.date());

		if (o1.id() != null && o2.id() != null)
			return o1.id().compareTo(o2.id());

		return 0;
	}).reversed();

	DomAtomPodcastService(URL feedUrl) {
		this.feedUrl = feedUrl;
	}

	@Override
	public Collection<Podcast> getPodcasts() {
		return this.podcasts;
	}

	@Override
	public void onApplicationEvent(IndexingFinishedEvent event) {
		this.refresh();
	}

	/**
	 * the site rebuilds once a day, but an episode goes out every week or so, and the
	 * feed is cheap to read, so this doesn't wait for a rebuild to notice one.
	 */
	@Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
	void refresh() {
		try {
			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			var builder = factory.newDocumentBuilder();
			try (var feedUrl = this.feedUrl.openStream()) {
				var doc = builder.parse(feedUrl);
				doc.getDocumentElement().normalize();
				var episodes = parseEntries(doc);
				episodes.sort(this.comparator);
				synchronized (this.monitor) {
					this.podcasts.clear();
					this.podcasts.addAll(episodes);
				}
			}
		} //
		catch (Exception e) {
			// keep the episodes we have: the next refresh will try again
			this.log.warn("could not read the podcast feed {}", this.feedUrl, e);
		}

	}

	private List<Podcast> parseEntries(Document doc) {
		var episodes = new ArrayList<Podcast>();
		var entryNodes = doc.getElementsByTagName("entry");
		for (var i = 0; i < entryNodes.getLength(); i++) {
			var entryNode = entryNodes.item(i);
			if (entryNode.getNodeType() == Node.ELEMENT_NODE) {

				var entryElement = (Element) entryNode;

				var title = "";
				var summary = "";
				var episodeHref = "";
				var imageHref = "";
				var updated = new Date();
				var uuid = "";

				// Parse title
				var titleNodes = entryElement.getElementsByTagName("title");
				if (titleNodes.getLength() > 0) {
					title = titleNodes.item(0).getTextContent();
				}

				// Parse link
				var rel = "rel";
				var linkNodes = entryElement.getElementsByTagName("link");
				for (var j = 0; j < linkNodes.getLength(); j++) {
					var linkElement = (Element) linkNodes.item(j);
					var relValue = linkElement.getAttribute(rel);
					var hrefValue = linkElement.getAttribute("href");
					if (StringUtils.hasText(relValue) && StringUtils.hasText(hrefValue)) {
						imageHref = hrefValue;
					} //
					else {
						episodeHref = hrefValue;
					}
				}

				// Parse summary
				var summaryNodes = entryElement.getElementsByTagName("summary");
				if (summaryNodes.getLength() > 0) {
					summary = summaryNodes.item(0).getTextContent();
				}

				// Parse updated date
				var updatedNodes = entryElement.getElementsByTagName("updated");
				if (updatedNodes.getLength() > 0) {
					var updatedStr = updatedNodes.item(0).getTextContent();
					try {
						updated = Date.from(Instant.parse(updatedStr.trim()));
					}
					catch (DateTimeParseException e) {
						this.log.warn("could not parse the date {}", updatedStr, e);
					}
				}

				// the entry's id is a urn:uuid:..., and mogul's own id for the episode is
				// in
				// its metadata
				var idNodes = entryElement.getElementsByTagNameNS(ATOM_NAMESPACE, "id");
				if (idNodes.getLength() > 0) {
					uuid = idNodes.item(0).getTextContent().trim().replaceFirst("^urn:uuid:", "");
				}
				var id = 0;
				var metadataNodes = entryElement.getElementsByTagNameNS("*", "metadata");
				if (metadataNodes.getLength() > 0) {
					var metadataIdNodes = ((Element) metadataNodes.item(0)).getElementsByTagNameNS("*", "id");
					if (metadataIdNodes.getLength() > 0) {
						id = Integer.parseInt(metadataIdNodes.item(0).getTextContent().trim());
					}
				}
				episodes.add(new Podcast(id, uuid, title, updated, url(imageHref), url(episodeHref), summary));
			}
		}

		return episodes;
	}

	private static URL url(String u) {
		try {
			return URI.create(u).toURL();
		} //
		catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

}
