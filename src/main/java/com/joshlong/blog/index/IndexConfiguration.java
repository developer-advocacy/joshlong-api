package com.joshlong.blog.index;

import com.joshlong.blog.BlogPostService;
import com.joshlong.blog.BlogProperties;
import com.joshlong.blog.IndexService;
import com.joshlong.blog.dates.IsoDateFormat;
import com.joshlong.blog.dates.SimpleDateDateFormat;
import com.joshlong.lucene.LuceneTemplate;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.DateFormat;

@Log4j2
@Configuration
public class IndexConfiguration {

	@Bean
	public IndexService indexService(@SimpleDateDateFormat DateFormat simpleDateFormat,
			ApplicationEventPublisher publisher, BlogProperties properties, BlogPostService blogPostService,
			LuceneTemplate luceneTemplate) throws Exception {
		return new DefaultIndexService(simpleDateFormat, publisher, blogPostService, luceneTemplate,
				properties.gitRepository(), properties.localCloneDirectory().getFile(), properties.resetOnRebuild());
	}

	@Component
	public static class Listener {

		private final DateFormat dateFormat;

		Listener(@IsoDateFormat DateFormat dateFormat) {
			this.dateFormat = dateFormat;
		}

		@EventListener
		public void indexStarted(IndexingStartedEvent startedEvent) {
			log.info("index build started " + this.dateFormat.format(startedEvent.getSource()));
		}

		@EventListener
		public void indexFinished(IndexingFinishedEvent finishedEvent) {
			log.info("index build finished " + this.dateFormat.format(finishedEvent.getSource()));
		}

	}

}
