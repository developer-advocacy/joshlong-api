package com.joshlong.blog.index;

import com.joshlong.blog.*;
import com.joshlong.lucene.DocumentWriteMapper;
import com.joshlong.lucene.LuceneTemplate;
import lombok.SneakyThrows;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.*;
import org.apache.lucene.index.Term;
import org.eclipse.jgit.api.Git;
import org.jsoup.Jsoup;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class DefaultIndexService
		implements IndexService/* , ApplicationListener<ApplicationReadyEvent> */ {

	private final static Log log = LogFactory.getLog(DefaultIndexService.class);

	private final DateFormat simpleDateFormat;

	private final File root;

	private final Map<String, BlogPost> index = new ConcurrentHashMap<>();

	private final ApplicationEventPublisher publisher;

	private final BlogPostService blogPostService;

	private final Object monitor = new Object();

	private final LuceneTemplate luceneTemplate;

	private final URI gitRepository;

	private final boolean resetOnRebuild;

	private final Set<String> extensions = Arrays.stream(BlogPostContentType.values())//
			.map(contentType -> contentType.name().toLowerCase(Locale.ROOT))//
			.collect(Collectors.toSet());

	@SneakyThrows
	DefaultIndexService(DateFormat simpleDateFormat, ApplicationEventPublisher publisher,
			BlogPostService blogPostService, LuceneTemplate luceneTemplate, URI gitRepository, File contentRoot,
			boolean resetOnRebuild) {
		this.simpleDateFormat = simpleDateFormat;
		this.blogPostService = blogPostService;
		this.resetOnRebuild = resetOnRebuild;
		this.luceneTemplate = luceneTemplate;
		this.root = contentRoot;
		this.publisher = publisher;
		this.gitRepository = gitRepository;
	}

	@SneakyThrows
	private void ensureClonedRepository() {

		if (!this.resetOnRebuild)
			return;

		if (this.root.exists() && this.root.isDirectory()) {
			log.info("deleting " + this.root.getAbsolutePath() + '.');
			FileSystemUtils.deleteRecursively(this.root);
		}

		var repo = Git.cloneRepository().setDirectory(this.root).setURI(this.gitRepository.toString()).call()
				.getRepository();

		try (var git = new Git(repo)) {
			var status = git.status().call();
			log.info("the status is " + status.toString());
		}
	}

	@Override
	@EventListener(SiteUpdatedEvent.class)
	public IndexRebuildStatus rebuildIndex() {
		log.info("refreshing " + IndexService.class.getName());
		Assert.notNull(this.root, () -> "you must specify a valid root ");
		this.publisher.publishEvent(new IndexingStartedEvent(new Date()));
		this.ensureClonedRepository();
		Assert.state(this.root.exists() && Objects.requireNonNull(this.root.list()).length > 0,
				() -> "there's no cloned repository under the root " + this.root.getAbsolutePath() + '.');
		synchronized (this.monitor) {
			this.index.clear();
			this.index.putAll(this.buildIndex());
		}
		var now = new Date();

		Assert.state(this.index.size() > 0, () -> "there are no entries in the content index. "
				+ "Something's wrong! Ensure you have content registered.");
		this.publisher.publishEvent(new IndexingFinishedEvent(now));
		return new IndexRebuildStatus(this.index.size(), now);

	}

	@Override
	public Map<String, BlogPost> getIndex() {
		return this.index;
	}

	@Override
	@SneakyThrows
	public Collection<BlogPost> search(String query) {
		var maxResults = 1000;
		return searchIndex(query, maxResults).stream().map(this.index::get).collect(Collectors.toSet());
	}

	private List<String> searchIndex(String queryStr, int maxResults) {
		return this.luceneTemplate.search(queryStr, maxResults, document -> document.get("path"));
	}

	private String computePath(File file, File contentDirectory) {
		var ext = ".md";
		var sub = file.getAbsolutePath().substring(contentDirectory.getAbsolutePath().length());
		if (sub.endsWith(ext))
			sub = sub.substring(0, sub.length() - ext.length()) + ".html";
		return sub.toLowerCase(Locale.ROOT);
	}

	@SneakyThrows
	private Map<String, BlogPost> buildIndex() {
		log.debug("building index @ " + Instant.now() + '.');
		var contentDirectory = new File(this.root, "content");
		var mapOfContent = Files.walk(contentDirectory.toPath()) //
				.parallel() //
				.map(Path::toFile) //
				.filter(this::isValidFile) //
				.map(file -> {
					var blogPost = blogPostService.buildBlogPostFrom(computePath(file, contentDirectory), file);
					return new DefaultIndexService.BlogPostKey(blogPost.path(), blogPost);
				}).collect(Collectors.toMap(DefaultIndexService.BlogPostKey::path,
						DefaultIndexService.BlogPostKey::blogPost));
		this.luceneTemplate.write(mapOfContent.entrySet(), entry -> {
			var path = entry.getKey();
			var blogPost = entry.getValue();
			var doc = buildBlogPost(path, blogPost);
			return new DocumentWriteMapper.DocumentWrite(new Term("key", buildHashKeyFor(blogPost)), doc);
		});
		return mapOfContent;
	}

	private String buildHashKeyFor(BlogPost blogPost) {
		Assert.notNull(blogPost, () -> "the blog must not be null");
		Assert.notNull(blogPost.date(), () -> "the blog date must not be null");
		Assert.notNull(blogPost.title(), () -> "the blog title must not be null");
		var title = blogPost.title();
		var stringBuilder = new StringBuilder();
		for (var c : title.toCharArray()) {
			if (Character.isAlphabetic(c)) {
				stringBuilder.append(c);
			}
		}
		return stringBuilder + this.simpleDateFormat.format(blogPost.date());
	}

	private String htmlToText(String htmlMarkup) {
		return Jsoup.parse(htmlMarkup).text();
	}

	@SneakyThrows
	private Document buildBlogPost(String relativePath, BlogPost post) {
		var document = new Document();
		document.add(new TextField("title", post.title(), Field.Store.YES));
		document.add(new TextField("path", relativePath, Field.Store.YES));
		document.add(new TextField("originalContent", post.originalContent(), Field.Store.YES));
		document.add(new TextField("content", htmlToText(post.processedContent()), Field.Store.YES));
		document.add(new LongPoint("time", post.date().getTime()));
		document.add(new StringField("key", buildHashKeyFor(post), Field.Store.YES));
		return document;
	}

	private boolean isValidFile(File fileName) {
		var lcFn = fileName.getName().toLowerCase(Locale.ROOT);
		for (var e : this.extensions)
			if (lcFn.contains(e))
				return true;
		return false;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationEvent() {
		this.rebuildIndex();
	}

	static private record BlogPostKey(String path, BlogPost blogPost) {
	}

}
