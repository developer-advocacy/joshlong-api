package com.joshlong.sitegenerator;

import com.joshlong.lucene.DocumentWriteMapper;
import com.joshlong.lucene.LuceneTemplate;
import com.joshlong.templates.MarkdownService;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.*;
import org.apache.lucene.index.Term;
import org.eclipse.jgit.api.Git;
import org.jsoup.Jsoup;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.FileSystemUtils;
import reactor.core.publisher.Mono;

import java.io.*;
import java.lang.annotation.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This the GraphQL API for the new joshlong.com. Most o the endpoints are GraphQ the
 * exception of a few endpoints intended to simplify integration, like one for Github's
 * webhooks.
 * <p>
 * It listens for webhooks from Github to know when to download and re-index the html
 * pages with a Spring Batch job.
 * <p>
 * It supports searching the blog posts with an in-memory Lucene index.
 *
 * @author <a href="mailto:josh@joshlong.com">Josh Long</a>
 */

@SpringBootApplication
@EnableConfigurationProperties(BlogProperties.class)
public class SiteApplication {

	public static void main(String[] args) {
		SpringApplication.run(SiteApplication.class, args);
	}

}

@Log4j2
@Controller
class ApiGraphQlController {

	private final IndexService indexService;

	private final DateFormat isoDateFormat;

	ApiGraphQlController(IndexService indexService, DateFormat isoDateFormat) {
		this.indexService = indexService;
		this.isoDateFormat = isoDateFormat;
	}

	@QueryMapping
	Collection<BlogPost> blogPosts() {
		return this.indexService.getIndex().values();
	}

	@QueryMapping
	Mono<BlogPost> blogPostByPath(@Argument String path) {
		var index = this.indexService.getIndex();
		var nk = path.toLowerCase(Locale.ROOT);
		return index.containsKey(nk) ? Mono.just(index.get(nk)) : Mono.empty();
	}

	@MutationMapping
	IndexRebuildStatus rebuildIndex() {
		return this.indexService.rebuildIndex();
	}

	@QueryMapping
	Collection<BlogPost> search(@Argument String query) {
		return this.indexService.search(query);
	}

	@SchemaMapping(typeName = "BlogPost", field = "date")
	String date(BlogPost bp) {
		return isoDateFormat.format(bp.date());
	}

	@SchemaMapping(typeName = "IndexRebuildStatus", field = "date")
	String indexRebuildStatusDate(IndexRebuildStatus rebuildStatus) {
		return isoDateFormat.format(rebuildStatus.date());
	}

}

record IndexRebuildStatus(int entries, Date date) {
}

@ConstructorBinding
@ConfigurationProperties("blog")
record BlogProperties(URI gitRepository, Resource localCloneDirectory, boolean resetOnRebuild) {
}

@Configuration
class BlogConfiguration {

	@Bean
	@SneakyThrows
	IndexService indexService(ApplicationEventPublisher publisher, BlogProperties properties,
			BlogPostService blogPostService, LuceneTemplate luceneTemplate) {
		return new DefaultIndexService(publisher, blogPostService, luceneTemplate, properties.gitRepository(),
				properties.localCloneDirectory().getFile(), properties.resetOnRebuild());
	}

	@Bean
	BlogPostService blogService(MarkdownService markdownService, @SimpleDateDateFormat DateFormat simpleDateFormat) {
		return new DefaultBlogPostService(markdownService, simpleDateFormat);
	}

}

enum BlogPostContentType {

	HTML, MD

}

record BlogPost(String title, Date date, String originalContent, String processedContent, boolean published,
		BlogPostContentType type, String path, List<String> images) {
}

@Log4j2
class DefaultBlogPostService implements BlogPostService {

	private final MarkdownService markdownService;

	private final DateFormat simpleDateFormat;

	DefaultBlogPostService(MarkdownService markdownService, DateFormat simpleDateFormat) {
		this.markdownService = markdownService;
		this.simpleDateFormat = simpleDateFormat;
	}

	@SneakyThrows
	private BlogPost buildBlogPostFrom(BlogPostContentType type, String path, InputStream file) {
		try (var f = new InputStreamReader(file)) {
			var contents = FileCopyUtils.copyToString(f);
			return buildBlogPostFrom(type, path, contents);
		}
	}

	@SneakyThrows
	@Override
	public BlogPost buildBlogPostFrom(String path, File file) {
		if (log.isDebugEnabled()) {
			log.debug("------");
			log.debug("indexing " + file.getAbsolutePath() + " with path " + path);
		}
		Assert.notNull(file, () -> "the file must not be null");
		Assert.state(file.exists(), () -> "the file " + file.getAbsolutePath() + " does not exist!");
		var type = file.getName().toLowerCase(Locale.ROOT).endsWith(".md") ? BlogPostContentType.MD
				: BlogPostContentType.HTML;
		return buildBlogPostFrom(type, path, new FileInputStream(file));
	}

	/*
	 * Given the HTML, we can use JSoup to discover the source attributes for all images
	 * in the markup
	 */
	private List<String> discoverImages(String html) {
		var results = new ArrayList<String>();
		var document = Jsoup.parse(html);
		var images = document.getElementsByTag("img");
		if (images != null && images.size() > 0) {
			images.forEach(element -> {
				if (element.hasAttr("src"))
					results.add(element.attr("src"));
			});
		}
		return results;
	}

	@SneakyThrows
	private BlogPost buildBlogPostFrom(BlogPostContentType type, String path, String contents) {
		var headerDivider = "~~~~~~";
		Assert.state(contents.contains(headerDivider), () -> "this blog  does not contain any headers! " + contents);
		var parts = contents.split(headerDivider);
		var header = buildHeader(parts[0]);
		var dateFromHeaderString = header.get("date");
		Assert.notNull(dateFromHeaderString, () -> "the blog must have a published date!");
		var date = buildHeaderDate(dateFromHeaderString);
		var processedContent = this.markdownService.convertMarkdownTemplateToHtml(parts[1]);
		var published = header.get("status").toLowerCase(Locale.ROOT).equalsIgnoreCase("published");
		var images = discoverImages(processedContent);
		return new BlogPost(header.get("title"), date, contents, processedContent, published, type, path, images);
	}

	@SneakyThrows
	private Date buildHeaderDate(String date) {
		Assert.state(3 == date.split("-").length, () -> "there should be 3 parts to the date");
		return this.simpleDateFormat.parse(date);
	}

	@SneakyThrows
	private Map<String, String> buildHeader(String header) {
		try (var sw = new StringReader(header)) {
			var props = new Properties();
			props.load(sw);
			var map = new HashMap<String, String>();
			for (var key : props.keySet())
				map.put((String) key, (props.getProperty((String) key) + "").trim());
			return map;
		}
	}

}

@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Qualifier("isoDateFormat")
@interface IsoDateFormat {

}

@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Qualifier("simpleDateDateFormat")
@interface SimpleDateDateFormat {

}

@Log4j2
@Configuration
class DateFormatConfiguration {

	@Bean
	@IsoDateFormat
	DateFormat isoDateFormat() {
		return DateFormatUtils.getThreadsafeIsoDateTimeDateFormat();
	}

	@Bean
	@SimpleDateDateFormat
	DateFormat simpleDateDateFormat() {
		return DateFormatUtils.getThreadSafeSimpleDateDateFormat();
	}

}

@Log4j2
abstract class ThreadLocalUtils {

	private static final Map<String, ThreadLocal<Object>> threadLocalMap = new ConcurrentHashMap<>();

	public static <T> T buildThreadLocalObject(String beanName, Class<T> clazz, Supplier<T> supplier) {

		var pfb = new ProxyFactory();
		for (var i : clazz.getInterfaces()) {
			pfb.addInterface(i);
		}
		pfb.setTarget(supplier.get());
		pfb.setTargetClass(clazz);
		pfb.setProxyTargetClass(true);
		pfb.addAdvice((MethodInterceptor) invocation -> {
			log.debug("invoking " + invocation.getMethod().getName() + " for beanName " + beanName + " on thread "
					+ Thread.currentThread().getName() + '.');
			var tl = threadLocalMap.computeIfAbsent(beanName, s -> new ThreadLocal<>());
			if (tl.get() == null) {
				log.debug("There is no bean instance of type " + clazz.getName() + " " + "on the thread "
						+ Thread.currentThread().getName() + ". " + "Constructing an instance by calling the supplier");
				tl.set(supplier.get());
			}
			log.debug(
					"fetching an instance of " + clazz.getName() + " for the thread " + Thread.currentThread().getName()
							+ " and there are " + threadLocalMap.size() + " thread local(s)");
			var obj = tl.get();
			var method = invocation.getMethod();
			return method.invoke(obj, invocation.getArguments());
		});

		return (T) pfb.getProxy();
	}

}

/**
 * Dates. What even. Ammirite?
 */
@Log4j2
abstract class DateFormatUtils {

	/*
	 * I want to use java.text.SimpleDateFormat.class directly in the proxies, but I get
	 * oddities related to modules, so this seems to be a workaround.
	 */
	static class SiteSimpleDateFormat extends SimpleDateFormat {

		public SiteSimpleDateFormat(String pattern) {
			super(pattern);
		}

	}

	public static SimpleDateFormat getThreadsafeIsoDateTimeDateFormat() {
		return ThreadLocalUtils.buildThreadLocalObject("isoDateTimeDateFormat", SiteSimpleDateFormat.class,
				() -> (SiteSimpleDateFormat) getIsoDateTimeDateFormat());
	}

	public static SimpleDateFormat getThreadSafeSimpleDateDateFormat() {
		return ThreadLocalUtils.buildThreadLocalObject("simpleDateDateFormat", SiteSimpleDateFormat.class,
				() -> (SiteSimpleDateFormat) getSimpleDateDateFormat());
	}

	private static SimpleDateFormat getIsoDateTimeDateFormat() {
		/* Quoted "Z" to indicate UTC, no timezone offset */
		var tz = TimeZone.getTimeZone("UTC");
		var df = new SiteSimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
		df.setTimeZone(tz);
		return df;
	}

	private static SimpleDateFormat getSimpleDateDateFormat() {
		return new SiteSimpleDateFormat("y-M-d");
	}

}

class DefaultIndexService implements IndexService, ApplicationListener<ApplicationReadyEvent> {

	private final static Log log = LogFactory.getLog(DefaultIndexService.class);

	private final int maxResults = 1000;

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
	DefaultIndexService(ApplicationEventPublisher publisher, BlogPostService blogPostService,
			LuceneTemplate luceneTemplate, URI gitRepository, File contentRoot, boolean resetOnRebuild) {
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
			// Equivalent of --> $ git branch -a
			var status = git.status().call();
			log.info("the status is " + status.toString());
		}

	}

	@Override
	public IndexRebuildStatus rebuildIndex() {

		this.ensureClonedRepository();

		synchronized (this.monitor) {
			this.index.clear();
			this.index.putAll(this.buildIndex());
		}
		var now = new Date();
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
		return searchIndex(query, this.maxResults).stream().map(this.index::get).collect(Collectors.toSet());
	}

	private List<String> searchIndex(String queryStr, int maxResults) throws Exception {
		return this.luceneTemplate.search(queryStr, maxResults, document -> document.get("path"));
	}

	private String computePath(File file, File contentDirectory) {
		var ext = ".md";
		var sub = file.getAbsolutePath().substring(contentDirectory.getAbsolutePath().length());
		if (sub.endsWith(ext))
			sub = sub.substring(0, sub.length() - ext.length()) + ".html";
		return sub.toLowerCase(Locale.ROOT);
	}

	static private record BlogPostKey(String path, BlogPost blogPost) {
	}

	@SneakyThrows
	private Map<String, BlogPost> buildIndex() {
		log.info("building index @ " + Instant.now() + '.');
		var contentDirectory = new File(this.root, "content");
		var mapOfContent = Files.walk(contentDirectory.toPath()) //
				.parallel() //
				.map(Path::toFile) //
				.filter(this::isValidFile) //
				.map(file -> {
					var blogPost = blogPostService.buildBlogPostFrom(computePath(file, contentDirectory), file);
					return new BlogPostKey(blogPost.path(), blogPost);
				}).collect(Collectors.toMap(BlogPostKey::path, BlogPostKey::blogPost));

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
		var ns = new StringBuilder();
		for (var c : title.toCharArray())
			if (Character.isAlphabetic(c))
				ns.append(c);
		return ns.toString() + blogPost.date().getYear() + blogPost.date().getMonth() + blogPost.date().getDate();
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
		document.add(new IntPoint("published", post.published() ? 1 : 0));
		return document;
	}

	private boolean isValidFile(File fileName) {
		var lcFn = fileName.getName().toLowerCase(Locale.ROOT);
		for (var e : this.extensions)
			if (lcFn.contains(e))
				return true;
		return false;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		this.rebuildIndex();
	}

}

interface IndexService {

	IndexRebuildStatus rebuildIndex();

	Collection<BlogPost> search(String query);

	Map<String, BlogPost> getIndex();

}

interface BlogPostService {

	BlogPost buildBlogPostFrom(String path, File file);

}

class IndexingFinishedEvent extends ApplicationEvent {

	public IndexingFinishedEvent(Date date) {
		super(date);
	}

}