package com.joshlong.blog.springtips;


import com.joshlong.blog.dates.IsoDateFormat;
import com.joshlong.blog.dates.SimpleDateDateFormat;
import com.joshlong.blog.index.IndexingFinishedEvent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.text.DateFormat;
import java.util.*;

/**
 * Manages all information related to new Spring Tips episodes. The source .json file in turn is derived from a
 * Google Docs spreadsheet whose contents are periodically synchronized to a Github repository. So, this isn't
 * necessarily the most <em>live</em> data.
 *
 * @author Josh Long
 */
@Slf4j
@Service
class DefaultSpringTipsService implements SpringTipsService {

    private final URI episodes = URI.create("https://springtipslive.io/episodes.json");
    private final List<SpringTipsEpisode> episodeList = new ArrayList<>();
    private final WebClient client;
    private final DateFormat dateFormat;
    private final Object monitor = new Object();

    DefaultSpringTipsService(@SimpleDateDateFormat DateFormat dateFormat, WebClient client) {
        this.client = client;
        this.dateFormat = dateFormat;
    }

    @EventListener(IndexingFinishedEvent.class)
    public void indexFinished() {
        reset();
    }

    private void reset() {
        log.info("reset(). Going to refresh the Spring Tips episodes from " + this.episodes);
        var list = this.client
                .get()
                .uri(this.episodes)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<Map<String, String>>() {
                })
                .collectList()
                .block();

        synchronized (this.monitor) {
            this.episodeList.clear();
            this.episodeList.addAll(Objects.requireNonNull(list).stream().map(this::from).toList());
            log.info("there are " + this.episodeList.size() + " episodes. ");
        }
    }

    @SneakyThrows
    private SpringTipsEpisode from(Map<String, String> map) {
        var dateString = this.dateFormat.parse(map.get("date"));
        return new SpringTipsEpisode(
                URI.create(map.get("blog_url")),
                dateString,
                Integer.parseInt(map.get("season_number")),
                map.get("title"),
                URI.create(map.get("youtube_embed_url")),
                map.get("youtube_id")
        );
    }

    @Override
    public SpringTipsEpisode getLatestSpringTipsEpisode() {
        return this.episodeList
                .stream()
                .max(Comparator.comparing(SpringTipsEpisode::date))
                .get();
    }

    @Override
    public Collection<SpringTipsEpisode> getSpringTipsEpisodes() {
        return new ArrayList<>(this.episodeList);
    }
}


