package com.joshlong.blog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * TODO Could this be made into an Actuator endpoint and could that Actuator endpoint feature some HTTP BASIC Spring Security?
 *
 * @author Josh Long
 */
@Slf4j
@Controller
@ResponseBody
class IndexWebhookRestController {

    private final IndexService indexService;

    private final BlogProperties properties;

    IndexWebhookRestController(IndexService indexService, BlogProperties properties) {
        this.indexService = indexService;
        this.properties = properties;
    }

    @PostMapping("/index")
    ResponseEntity<?> refresh(

            @RequestBody Map<String, String> payload) {
        payload.forEach((k, v) -> log.info(k + "=" + v));
        var content = payload.getOrDefault("key", "");
        var good = (StringUtils.hasText(content) && this.properties.indexRebuildKey().equalsIgnoreCase(content));
        return good ? ResponseEntity.ok(this.indexService.rebuildIndex()) : ResponseEntity.badRequest().build();
    }
}
