package com.joshlong.blog.index;

import com.joshlong.blog.BlogPostService;
import com.joshlong.blog.BlogProperties;
import com.joshlong.blog.IndexService;
import com.joshlong.blog.dates.IsoDateFormat;
import com.joshlong.blog.dates.SimpleDateDateFormat;
import com.joshlong.lucene.LuceneTemplate;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.CoreConfig;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.util.Set;

@Slf4j
@Configuration
@ImportRuntimeHints(IndexConfiguration.Hints.class)
public class IndexConfiguration {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

			hints.resources().registerResourceBundle("org.eclipse.jgit.internal.JGitText");

			Set.of(CoreConfig.AutoCRLF.class, CoreConfig.CheckStat.class, CoreConfig.EOL.class,
					CoreConfig.HideDotFiles.class, CoreConfig.EolStreamType.class, CoreConfig.LogRefUpdates.class,
					CoreConfig.SymLinks.class, org.eclipse.jgit.internal.JGitText.class)
					.forEach(clzz -> hints.reflection().registerType(clzz, MemberCategory.values()));
		}

	}

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
			log.info("index build finished " + this.dateFormat.format(finishedEvent.getSource().date()));
		}

	}

}
