package com.joshlong.blog;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
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

	IndexWebhookRestController(IndexService indexService, BlogProperties properties) throws Exception {
		this.indexService = indexService;
		this.properties = properties;
		this.computedKey = this.deriveKey();
		log.info("the computed key is " + this.computedKey);
	}

	@SneakyThrows
	private String deriveKey() {
		return (this.properties.indexRebuildKey());

	}

	@PostMapping("/index")
	ResponseEntity<?> refresh(RequestEntity<String> requestEntity) throws Exception {
		var secret = this.properties.indexRebuildKey();
		var hash = HmacUtils.generateHmac256(Objects.requireNonNull(requestEntity.getBody()), secret.getBytes());
		log.info("the hash is [" + hash + "]");
		var headers = requestEntity.getHeaders();
		headers.forEach((k, v) -> log.info(k + "=" + v));
		var headerKey = "X-Hub-Signature-256";
		if (headers.containsKey(headerKey)) {
			var strings = headers.get(headerKey);
			if (Objects.requireNonNull(strings).size() > 0) {
				var key = strings.get(0);
				if (key.contains(this.deriveKey())) {
					log.info("rebuilding index successfully");
					return ResponseEntity.ok(this.indexService.rebuildIndex());
				} //
				else {
					log.info("the key " + deriveKey() + " is not within " + key);
				}
			}
		}
		return ResponseEntity.badRequest().build();
	}

}

abstract class HmacUtils {

	public static String generateHmac256(String message, byte[] key)
			throws InvalidKeyException, NoSuchAlgorithmException {
		byte[] bytes = hmac("HmacSHA256", key, message.getBytes());
		return bytesToHex(bytes);
	}

	private static byte[] hmac(String algorithm, byte[] key, byte[] message)
			throws NoSuchAlgorithmException, InvalidKeyException {
		Mac mac = Mac.getInstance(algorithm);
		mac.init(new SecretKeySpec(key, algorithm));
		return mac.doFinal(message);
	}

	private static String bytesToHex(byte[] bytes) {
		final char[] hexArray = "0123456789abcdef".toCharArray();
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0, v; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException {
		var valueToDigest = "The quick brown fox jumps over the lazy dog";
		var key = "1234".getBytes();
		var messageDigest = HmacUtils.generateHmac256(valueToDigest, key);
		System.out.println(messageDigest);
	}

}