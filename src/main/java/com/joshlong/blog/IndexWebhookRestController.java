package com.joshlong.blog;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.util.Hex;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

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
		return Hex.toHexString(hash);
	}

	@PostMapping("/index")
	ResponseEntity<?> refresh(RequestEntity<?> requestEntity) {
		// log.info("index:key: " + key);

		HttpHeaders headers = requestEntity.getHeaders();
		headers.forEach((k, v) -> log.info(k + "=" + v));

		var headerKey = "X-Hub-Signature-256";

		if (headers.containsKey(headerKey)) {
			List<String> strings = headers.get(headerKey);
			if (Objects.requireNonNull(strings).size() > 0) {
				var key = strings.get(0);
				if (key.contains(this.deriveKey())) {
					log.info("rebuilding index successfully");
					return ResponseEntity.ok(this.indexService.rebuildIndex());
				}
				else {
					log.info("the key " + deriveKey() + " is not within " + key);

				}
			}
		}

		return ResponseEntity.badRequest().build();
	}

}
