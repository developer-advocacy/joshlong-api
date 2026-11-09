package com.joshlong;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;

import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IndexWebhookRestControllerTest {

	private static final String KEY = "the-key";

	private final CountDownLatch rebuildStarted = new CountDownLatch(1);

	private final CountDownLatch finishRebuild = new CountDownLatch(1);

	private final AtomicInteger rebuilds = new AtomicInteger();

	private final IndexService indexService = new IndexService() {

		@Override
		public IndexRebuildStatus rebuildIndex() throws Exception {
			rebuilds.incrementAndGet();
			rebuildStarted.countDown();
			finishRebuild.await();
			return new IndexRebuildStatus(1, new Date());
		}

		@Override
		public BlogPostSearchResults search(String query, int offset, int size, boolean includeListed) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<String, BlogPost> getIndex() {
			return Map.of();
		}

	};

	private final IndexWebhookRestController controller = new IndexWebhookRestController(this.indexService,
			new BlogProperties(null, null, false, null, null, KEY, new String[0], null));

	@Test
	void answersBeforeTheRebuildFinishes() throws Exception {
		var response = this.controller.refresh(this.signed("{\"ref\":\"refs/heads/master\"}", KEY));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(this.rebuildStarted.await(5, TimeUnit.SECONDS)).isTrue();
		this.finishRebuild.countDown();
	}

	@Test
	void rejectsTheWrongSignature() throws Exception {
		var response = this.controller.refresh(this.signed("{}", "not-the-key"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(this.rebuilds.get()).isZero();
	}

	private RequestEntity<String> signed(String body, String key) throws Exception {
		var signature = HmacUtils.generateHmac256(body, key.getBytes());
		return RequestEntity.post(URI.create("http://localhost/index"))
				.header("X-Hub-Signature-256", "sha256=" + signature).body(body);
	}

}
