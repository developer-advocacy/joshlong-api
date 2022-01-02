package com.joshlong.blog;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.util.Hex;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TODO Could this be made into an Actuator endpoint and could that Actuator endpoint
 * feature some HTTP BASIC Spring Security?
 *
 * @author Josh Long
 */
@Slf4j
@Controller
@ResponseBody
class IndexWebhookRestController {

	private final IndexService indexService;

	private final BlogProperties properties;

	private final String computedKey;

	IndexWebhookRestController(IndexService indexService, BlogProperties properties) throws Exception {

		this.indexService = indexService;
		this.properties = properties;
		this.computedKey = this.deriveKey();

	}

	@SneakyThrows
	private String deriveKey() {
		var key = this.properties.indexRebuildKey();

		var digest = MessageDigest.getInstance("SHA-256");
		var hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
		var sha256hex = (Hex.toHexString(hash));
		log.info("the sha256 is " + sha256hex);
		return sha256hex;
	}

	@PostMapping("/index")
	ResponseEntity<?> refresh(@RequestHeader("X-Hub-Signature-256") String key) {
		if (StringUtils.hasText(key)) {
			if (key.contains(this.computedKey)) {
				return ResponseEntity.ok(this.indexService.rebuildIndex());
			}
		}
		return ResponseEntity.badRequest().build();
	}

}
