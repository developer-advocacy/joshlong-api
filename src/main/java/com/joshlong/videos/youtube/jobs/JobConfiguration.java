package com.joshlong.videos.youtube.jobs;

import com.joshlong.twitter.Twitter;
import com.joshlong.videos.JobProperties;
import com.joshlong.videos.youtube.IngestJobInitiatedEvent;
import com.joshlong.videos.youtube.client.YoutubeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

@Configuration
class JobConfiguration {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Bean
	ApplicationListener<IngestJobInitiatedEvent> jobListener(JobProperties properties,
			CompositeIngestJob compositeIngestJob, PromotionJob promotion) {
		return event -> {
			if (!properties.batch().run()) {
				this.log.info("not running the ingest job because bootiful.batch.run=false");
				return;
			}
			try {
				compositeIngestJob.run();
				// the promotion tweets the videos it ingests, and there's no twitter
				// account behind it any more, so it runs only when it's asked to.
				if (properties.promotion().run())
					promotion.run();
				else
					this.log.info("not running the promotion job because bootiful.promotion.run=false");
			} //
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
	}

	@Bean
	CompositeIngestJob compositeIngestJob(YoutubeClient client, JdbcClient databaseClient, JobProperties properties) {
		var channelIds = properties.batch().channelIds();
		var compositeList = new Job[channelIds.length];
		var indx = 0;
		for (var username : channelIds)
			compositeList[indx++] = new IngestJob(client, databaseClient, username);
		return new CompositeIngestJob(compositeList);
	}

	static class CompositeIngestJob implements Job {

		private final Job[] jobs;

		CompositeIngestJob(Job[] jobs) {
			this.jobs = jobs;
		}

		@Override
		public void run() throws Exception {
			for (var job : this.jobs)
				job.run();
		}

	}

	@Bean
	JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	PromotionJob promotionJob(JdbcTemplate template, Twitter twitter, JobProperties properties) {
		var twitterProperties = properties.twitter();
		return new PromotionJob(template, twitter, twitterProperties.clientId(), twitterProperties.clientSecret(),
				twitterProperties.username(), properties.promotion().playlistIds());
	}

}
