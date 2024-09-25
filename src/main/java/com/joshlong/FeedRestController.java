package com.joshlong;

import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Produces an RSS feed of all the blogs
 */

@Controller
@ResponseBody
class FeedRestController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Feeds feeds;

	private final List<BlogPost> posts = new CopyOnWriteArrayList<>();

	private final AtomicReference<String> renderedXml = new AtomicReference<>();

	private final BlogProperties properties;

	FeedRestController(Feeds feeds, BlogProperties properties) {
		this.feeds = feeds;
		this.properties = properties;
	}

	@EventListener
	public void blogPostsUpdatedEvent(BlogPostsOrderedEvent updatedEvent) throws Exception {
		this.reset(updatedEvent.getSource());
	}

	private void reset(List<BlogPost> posts) throws Exception {
		synchronized (this.posts) {
			this.posts.clear();
			this.posts.addAll(posts);
			var blogPosts = this.posts.stream()//
					.map(new BlogPostSyndEntryConvertor())//
					.toList();
			var rss = properties.rss();
			if (log.isDebugEnabled()) {
				log.debug("rss == null ? " + (rss == null));
				log.debug(Map
						.of("title", "" + rss.title(), "link", "" + rss.link(), "description", "" + rss.description())
						.toString());
			}
			var feed = this.feeds.buildFeed("rss_2.0", rss.title(), rss.link(), rss.description(), blogPosts);
			var xml = this.feeds.render(feed);
			this.renderedXml.set(xml);
		}
	}

	@GetMapping(value = "/feed.xml", produces = MediaType.APPLICATION_RSS_XML_VALUE)
	String feed() throws Exception {
		return this.renderedXml.get();
	}

}

class BlogPostSyndEntryConvertor implements Function<BlogPost, SyndEntry> {

	@Override
	public SyndEntry apply(BlogPost post) {
		var entry = new SyndEntryImpl();
		entry.setTitle(post.title());
		entry.setLink(String.format("https://joshlong.com%s", post.path()));
		entry.setPublishedDate(post.date());

		var description = new SyndContentImpl();
		description.setType("text/plain");
		description.setValue(post.paragraphs().get(0));
		entry.setDescription(description);
		return entry;
	}

}

@Component
class Feeds {

	String render(SyndFeed feed) {
		try {
			var output = new SyndFeedOutput();

			return output.outputString(feed);
		}
		catch (FeedException e) {
			throw new RuntimeException(e);
		}
	}

	SyndFeed buildFeed(String feedType, String title, String link, String description, List<SyndEntry> posts) {
		var feed = new SyndFeedImpl();
		feed.setFeedType(feedType);
		feed.setTitle(title);
		feed.setLink(link);
		feed.setDescription(description);
		feed.setEntries(posts);
		return feed;
	}

}
