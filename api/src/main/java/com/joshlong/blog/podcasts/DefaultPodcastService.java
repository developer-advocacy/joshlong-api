package com.joshlong.blog.podcasts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.blog.Podcast;
import com.joshlong.blog.PodcastService;
import com.joshlong.blog.utils.JsonUtils;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
* {
id: "456",
uid: "c7172875-a743-436c-96b0-ba25b81b03d4",
title: "Swarn Podila on Cloud Foundry, Hashicorp and more",
date: 1606353095168,
episodePhotoUri: "https://mcdn.podbean.com/mf/web/z3encn/c7172875-a743-436c-96b0-ba25b81b03d4.jpg",
description: "<p>Hi, Spring fans! Welcome to another installment of <em>A Bootiful Podcast</em>! In this episode, <a href="http://twitter.com/starbuxman">Josh Long (@starbuxman)</a> talks to <a href="http://twitter.com/skpodila">Swarna Podila (@skpodila)</a> about Cloud Foundry, networks and Hashicorp, among other things.</p> <p>Happy Thanksgiving!</p> <ul> <li><a href="https://www.hashicorp.com/cloud-platform">Hashicorp Cloud Platform</a></li> <li><a href="https://www.waypointproject.io">Waypoint</a></li> <li><a href="https://cloudfoundry.org">Cloud Foundry</a></li> </ul>",
dateAndTime: "11/26/2020",
dataAndTime: "11/26/2020",
episodeUri: "/podcasts/c7172875-a743-436c-96b0-ba25b81b03d4/produced-audio"

* */
@Log4j2
class DefaultPodcastService implements PodcastService {

    private final URL root = new URL("http://api.bootifulpodcast.fm");
    private final URL uri = new URL(this.root + "/site/podcasts");

    private final Collection<Podcast> podcasts;

    @SneakyThrows
    private URL urlFrom(String url) {

        if (StringUtils.hasText(url)) {
            return new URL(url);
        }
        return null;
    }

    DefaultPodcastService(ObjectMapper objectMapper) throws IOException {
        log.info("the root url is " + this.root);
        log.info("the uri is " + this.uri);

        var response = objectMapper
                .readValue(this.uri, new TypeReference<Collection<JsonNode>>() {
                });

        this.podcasts = response
                .stream()
                .map(node -> {
                    var id = JsonUtils.valueOrNull(node, "id", Integer::parseInt);
                    var uid = JsonUtils.valueOrNull(node, "uid");
                    var title = JsonUtils.valueOrNull(node, "title");
                    var date = new Date(node.get("date").longValue());
                    var episodePhotoUri = JsonUtils.valueOrNull(node, "episodePhotoUri", this::urlFrom);
                    var episodeUri = JsonUtils.valueOrNull(node, "episodeUri", u -> urlFrom(root + u));
                    var description = JsonUtils.valueOrNull(node, "description");
                    return new Podcast(id, uid, title, date, episodePhotoUri, episodeUri, description);
                })
                .collect(Collectors.toList());

    }

    @Override
    public Collection<Podcast> getPodcasts() {
        return this.podcasts;
    }
}
