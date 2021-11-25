package com.joshlong.blog.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.blog.Content;
import com.joshlong.blog.ContentService;
import com.joshlong.blog.index.IndexingFinishedEvent;
import com.joshlong.blog.utils.JsonUtils;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Log4j2
class DefaultContentService implements ContentService {

    private final Resource resource;
    private final ObjectMapper objectMapper;
    private final List<Content> contents = new CopyOnWriteArrayList<>();

    DefaultContentService(Resource resource, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.resource = resource;
    }

    @Override
    public Collection<Content> getContent() {
        return this.contents;
    }

    @SneakyThrows
    private URL buildUrlFrom(String url) {
        return new URL(url);
    }

    @EventListener(IndexingFinishedEvent.class)
    public void indexed() throws Exception {
        log.info("building " + getClass().getName()  + " for file " + this.resource.getFile().getAbsolutePath() + '.');

        var file = this.resource.getFile();
        var values = this.objectMapper.readValue(file, new TypeReference<Collection<JsonNode>>() {
        });
        var content = values
                .stream()
                .map(json -> {
                    var title = JsonUtils.valueOrNull(json, "title");
                    var html = JsonUtils.valueOrNull(json, "html");
                    var imageUrl = buildUrlFrom(JsonUtils.valueOrNull(json, "imageUrl"));
                    return new Content(title, html, imageUrl);
                })
                .collect(Collectors.toList());

        synchronized (this.contents) {
            this.contents.clear();
            this.contents.addAll(content);
        }


    }
}

