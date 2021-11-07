package com.joshlong.blog.media;

import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;

/**
 * Serves media from the Github clone
 */
@Log4j2
@Controller
@ResponseBody
class MediaRestController {

	private static final String MEDIA_URI_PREFIX = "/media";

	private static final String MEDIA_URI = MEDIA_URI_PREFIX + "/**";

	private final File mediaRoot;

	MediaRestController(File root) {
		this.mediaRoot = root;
		var absolutePath = this.mediaRoot.getAbsolutePath();
		Assert.state(this.mediaRoot.exists(), () -> "the media directory " + absolutePath + " does not exist");
		log.debug("creating " + MediaRestController.class.getName() + " with media directory " + absolutePath);
	}

	@GetMapping(MEDIA_URI)
	ResponseEntity<Resource> readMedia(ServerHttpRequest request) {
		var path = request.getPath().toString().substring(MEDIA_URI_PREFIX.length());
		var file = new File(this.mediaRoot, path);
		if (!file.exists()) {
			log.debug("media file " + file.getAbsolutePath() + " does not exist.");
			return ResponseEntity //
					.notFound() //
					.build();
		} //
		else {
			log.debug("reading media file " + file.getAbsolutePath());
			var resource = new FileSystemResource(file);
			return ResponseEntity.ok() //
					.contentType(MediaType.IMAGE_JPEG) //
					.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*") //
					.body(resource);
		}
	}

}
