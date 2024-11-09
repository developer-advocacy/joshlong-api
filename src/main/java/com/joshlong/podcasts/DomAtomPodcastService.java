package com.joshlong.podcasts;

import com.joshlong.Podcast;
import com.joshlong.PodcastService;
import com.joshlong.index.IndexingFinishedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

class DomAtomPodcastService implements PodcastService, ApplicationListener<IndexingFinishedEvent> {

	private final Object monitor = new Object();

	private final Collection<Podcast> podcasts = new CopyOnWriteArrayList<>();

	private final URL feedUrl;

	private final URL rootHost;

	private final Comparator<Podcast> comparator = ((Comparator<Podcast>) (o1, o2) -> {

		if (o1.date() != null && o2.date() != null)
			return o1.date().compareTo(o2.date());

		if (o1.id() != null && o2.id() != null)
			return o1.id().compareTo(o2.id());

		return 0;
	}).reversed();

	DomAtomPodcastService(URL feedUrl) {
		this.feedUrl = feedUrl;
		this.rootHost = url(this.feedUrl.getProtocol() + "://" + this.feedUrl.getHost());
	}

	@Override
	public Collection<Podcast> getPodcasts() {
		return this.podcasts;
	}

	@Override
	public void onApplicationEvent(IndexingFinishedEvent event) {
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
			throw new RuntimeException(e);
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
						var dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
						updated = dateFormat.parse(updatedStr);
					}
					catch (ParseException e) {
						System.err.println("Error parsing date: " + updatedStr);
					}
				}

				// Parse UUID from metadata
				var metadataNodes = entryElement.getElementsByTagNameNS("http://api.media-mogul.io", "metadata");
				if (metadataNodes.getLength() > 0) {
					var metadataElement = (Element) metadataNodes.item(0);
					var uuidNodes = metadataElement.getElementsByTagNameNS("http://api.media-mogul.io", "uuid");
					if (uuidNodes.getLength() > 0) {
						uuid = uuidNodes.item(0).getTextContent();
					}
				}
				episodes.add(new Podcast(0, uuid, title, updated, url(imageHref), url(episodeHref), summary));
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
