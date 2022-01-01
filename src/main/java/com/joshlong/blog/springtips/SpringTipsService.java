package com.joshlong.blog.springtips;

import java.util.Collection;

public interface SpringTipsService {

    SpringTipsEpisode getLatestSpringTipsEpisode();

    Collection<SpringTipsEpisode> getSpringTipsEpisodes();
}
