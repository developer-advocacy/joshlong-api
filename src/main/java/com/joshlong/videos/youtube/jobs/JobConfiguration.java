package com.joshlong.videos.youtube.jobs;

import com.joshlong.twitter.Twitter;
import com.joshlong.videos.JobProperties;
import com.joshlong.videos.youtube.IngestJobInitiatedEvent;
import com.joshlong.videos.youtube.client.Playlist;
import com.joshlong.videos.youtube.client.YoutubeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;

@Configuration
class JobConfiguration {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Bean
	ApplicationListener<IngestJobInitiatedEvent> jobListener(JobProperties properties, IngestJob ingest,
			PromotionJob promotion) {
		return event -> {
			if (properties.batch().run())
				this.doRunIngestJob(ingest, promotion);
			else
				log.info("not running batch ingest and promotion job because bootiful.batch.run=false");
		};
	}

	private void doRunIngestJob(IngestJob ingest, PromotionJob promotion) {
		for (var job : new Job<?>[] { ingest, promotion })
			job.run();
	}

	@Bean
	IngestJob compositeIngestJob(YoutubeClient client, JdbcClient databaseClient, JobProperties properties) {
		var channelIds = properties.batch().channelIds();
		var compositeList = new IngestJob[channelIds.length];
		var indx = 0;
		for (var username : channelIds)
			compositeList[indx++] = new SimpleIngestJob(client, databaseClient, username);
		return new CompositeIngestJob(compositeList);
	}

	private static class CompositeIngestJob implements IngestJob {

		private final IngestJob[] jobs;

		CompositeIngestJob(IngestJob[] jobs) {
			this.jobs = jobs;
		}

		@Override
		public Collection<Playlist> run() {
			var list = new ArrayList<Playlist>();
			for (var j : this.jobs) {
				var run = j.run();
				list.addAll(run);
			}
			return list;
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
