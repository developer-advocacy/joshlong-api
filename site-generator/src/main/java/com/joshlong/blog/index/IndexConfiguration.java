package com.joshlong.blog.index;

import com.joshlong.blog.BlogPostService;
import com.joshlong.blog.BlogProperties;
import com.joshlong.blog.IndexService;
import com.joshlong.blog.dates.IsoDateFormat;
import com.joshlong.blog.dates.SimpleDateDateFormat;
import com.joshlong.lucene.LuceneTemplate;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.text.DateFormat;

@Configuration
@Log4j2
class IndexConfiguration {

	private final DateFormat dateFormat;

	IndexConfiguration(@IsoDateFormat DateFormat dateFormat) {
		this.dateFormat = dateFormat;
	}

	@EventListener
	public void indexStarted(IndexingStartedEvent startedEvent) {
		log.info("index build started " + this.dateFormat.format(startedEvent.getSource()));
	}

	@EventListener
	public void indexStopped(IndexingFinishedEvent finishedEvent) {
		log.info("index build stopped " + this.dateFormat.format(finishedEvent.getSource()));
	}

	@Bean
	@SneakyThrows
	IndexService indexService(@SimpleDateDateFormat DateFormat simpleDateFormat, ApplicationEventPublisher publisher,
			BlogProperties properties, BlogPostService blogPostService, LuceneTemplate luceneTemplate) {
		return new DefaultIndexService(simpleDateFormat, publisher, blogPostService, luceneTemplate,
				properties.gitRepository(), properties.localCloneDirectory().getFile(), properties.resetOnRebuild());
	}

}
