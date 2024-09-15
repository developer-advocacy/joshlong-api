package com.joshlong.videos.youtube.jobs;

import com.joshlong.twitter.Twitter;
import com.joshlong.videos.JobProperties;
import com.joshlong.videos.youtube.IngestJobInitiatedEvent;
import com.joshlong.videos.youtube.client.Playlist;
import com.joshlong.videos.youtube.client.YoutubeClient;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;

import java.util.ArrayList;

@Slf4j
@Configuration
class JobConfiguration {

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
		var justIngest = Flux.from(ingest.run());
		var justPromotion = Flux.from(promotion.run());
		var both = justIngest.thenMany(justPromotion).doOnError(ex -> log.error("something's gone wrong!", ex));
		both.subscribe();
	}

	@Bean
	ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory cf) {
		return new R2dbcTransactionManager(cf);
	}

	@Bean
	TransactionalOperator transactionalOperator(ReactiveTransactionManager rtm) {
		return TransactionalOperator.create(rtm);
	}

	@Bean
	IngestJob compositeIngestJob(YoutubeClient client, DatabaseClient databaseClient, JobProperties properties) {
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
		public Publisher<Playlist> run() {
			var list = new ArrayList<Publisher<Playlist>>();
			for (var j : this.jobs)
				list.add(j.run());
			return Flux.concat(list);
		}

	}

	@Bean
	PromotionJob promotionJob(DatabaseClient databaseClient, Twitter twitter, JobProperties properties) {
		var twitterProperties = properties.twitter();
		return new PromotionJob(databaseClient, twitter, twitterProperties.clientId(), twitterProperties.clientSecret(),
				twitterProperties.username(), properties.promotion().playlistIds());
	}

}
