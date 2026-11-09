package com.joshlong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GitHub calls this whenever the content repository changes. GitHub gives up on a webhook
 * after ten seconds, and a rebuild takes longer than that, so this answers right away and
 * rebuilds in the background, one rebuild at a time.
 * <p>
 * TODO Could this be made into an Actuator endpoint and could that Actuator endpoint
 * feature some HTTP BASIC Spring Security?
 *
 * @author Josh Long
 */
@Controller
@ResponseBody
class IndexWebhookRestController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final IndexService indexService;

	private final BlogProperties properties;

	private final ExecutorService rebuilds = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());

	private final AtomicBoolean rebuildQueued = new AtomicBoolean();

	IndexWebhookRestController(IndexService indexService, BlogProperties properties) {
		this.indexService = indexService;
		this.properties = properties;
	}

	@PostMapping("/index")
	ResponseEntity<?> refresh(RequestEntity<String> requestEntity) throws Exception {
		var secret = this.properties.indexRebuildKey();
		var theirHash = HmacUtils.generateHmac256(Objects.requireNonNull(requestEntity.getBody()), secret.getBytes());
		var myHash = getGithubWebhookRequestSha256HeaderValue(requestEntity);
		if (log.isDebugEnabled()) {
			requestEntity.getHeaders().forEach((k, v) -> log.debug(k + "=" + v));
			log.debug("mine: {}", theirHash);
			log.debug("theirs: {}", myHash);
		}
		if (StringUtils.hasText(myHash) && StringUtils.hasText(theirHash)) {
			if (myHash.contains(theirHash)) {
				this.requestRebuild();
				return ResponseEntity.accepted().build();
			}
		}
		return ResponseEntity.badRequest().build();
	}

	// a rebuild reads the whole repository, so pushes that arrive while one is waiting to
	// run share it.
	private void requestRebuild() {
		if (!this.rebuildQueued.compareAndSet(false, true))
			return;
		this.rebuilds.execute(() -> {
			this.rebuildQueued.set(false);
			try {
				var status = this.indexService.rebuildIndex();
				this.log.info("rebuilt the index for the webhook: {} entries", status.entries());
			}
			catch (Exception e) {
				this.log.error("couldn't rebuild the index for the webhook", e);
			}
		});
	}

	private String getGithubWebhookRequestSha256HeaderValue(RequestEntity<String> requestEntity) {
		var headers = requestEntity.getHeaders();
		var headerKey = "X-Hub-Signature-256";
		if (headers.containsKey(headerKey)) {
			var strings = headers.get(headerKey);
			if (!Objects.requireNonNull(strings).isEmpty()) {
				return strings.getFirst().trim();
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