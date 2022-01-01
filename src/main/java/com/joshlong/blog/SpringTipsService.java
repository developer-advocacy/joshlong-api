package com.joshlong.blog;

import java.util.Collection;

public interface SpringTipsService {

	SpringTipsEpisode getLatestSpringTipsEpisode();

	Collection<SpringTipsEpisode> getSpringTipsEpisodes();

}
