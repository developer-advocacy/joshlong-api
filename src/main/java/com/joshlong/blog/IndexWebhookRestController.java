package com.joshlong.blog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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

	IndexWebhookRestController(IndexService indexService, BlogProperties properties) {
		this.indexService = indexService;
		this.properties = properties;
		this.computedKey = this.properties.indexRebuildKey();
	}

	@PostMapping("/index")
	ResponseEntity<?> refresh(RequestEntity<String> requestEntity) throws Exception {
		var secret = this.properties.indexRebuildKey();
		var hashOfTheRequestWithMyCopyOfTheSecret = HmacUtils
				.generateHmac256(Objects.requireNonNull(requestEntity.getBody()), secret.getBytes());
		var hashOfTheRequestWithTheirCopyOfTheSecret = getGithubWebhookRequestSha256HeaderValue(requestEntity);

		log.info("mine: " + hashOfTheRequestWithMyCopyOfTheSecret);
		log.info("theirs: " + hashOfTheRequestWithTheirCopyOfTheSecret);

		if (StringUtils.hasText(hashOfTheRequestWithTheirCopyOfTheSecret)
				&& StringUtils.hasText(hashOfTheRequestWithMyCopyOfTheSecret)) {
			if (hashOfTheRequestWithTheirCopyOfTheSecret.contains(hashOfTheRequestWithMyCopyOfTheSecret))
				return ResponseEntity.ok(indexService.rebuildIndex());

		}

		return ResponseEntity.badRequest().build();
	}

	private String getGithubWebhookRequestSha256HeaderValue(RequestEntity<String> requestEntity) {
		var headers = requestEntity.getHeaders();
		headers.forEach((k, v) -> log.info(k + "=" + v));
		var headerKey = "X-Hub-Signature-256";
		if (headers.containsKey(headerKey)) {
			var strings = headers.get(headerKey);
			if (Objects.requireNonNull(strings).size() > 0) {
				return strings.get(0).trim();
			}
		}
		return null;
	}

}

/**
 * This was taken from <a href
 * ="https://www.javacodemonk.com/create-hmacsha256-signature-in-java-3421c36d">this blog
 * post</a>. Thanks, JavaCodeMonk.com!
 */
abstract class HmacUtils {

	public static String generateHmac256(String message, byte[] key)
			throws InvalidKeyException, NoSuchAlgorithmException {
		var bytes = hmac("HmacSHA256", key, message.getBytes());
		return bytesToHex(bytes);
	}

	private static byte[] hmac(String algorithm, byte[] key, byte[] message)
			throws NoSuchAlgorithmException, InvalidKeyException {
		var mac = Mac.getInstance(algorithm);
		mac.init(new SecretKeySpec(key, algorithm));
		return mac.doFinal(message);
	}

	private static String bytesToHex(byte[] bytes) {
		var hexArray = "0123456789abcdef".toCharArray();
		var hexChars = new char[bytes.length * 2];
		for (int j = 0, v; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

}