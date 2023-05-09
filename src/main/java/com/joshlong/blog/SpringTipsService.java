package com.joshlong.blog;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface SpringTipsService {

	Mono<SpringTipsEpisode> getLatestSpringTipsEpisode();

	Flux<SpringTipsEpisode> getSpringTipsEpisodes();

}
