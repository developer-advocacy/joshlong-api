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
	ApplicationListener<IngestJobInitiatedEvent> jobListener(
			JobProperties properties,
			CompositeIngestJob compositeIngestJob,
			PromotionJob promotion) {
		return event -> {
			if (properties.batch().run()) {
				try {
					for (var job : new Job[]{compositeIngestJob, promotion})
						job.run();
				} //  
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			else
				log.info("not running batch ingest and promotion job because bootiful.batch.run=false");
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

	private record CompositeIngestJob(Job[] jobs) implements Job {
		
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
