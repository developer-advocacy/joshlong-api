package com.joshlong.sitegenerator;

import com.joshlong.lucene.DocumentWriteMapper;
import com.joshlong.lucene.LuceneTemplate;
import com.joshlong.templates.MarkdownService;
import lombok.SneakyThrows;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.*;
import org.apache.lucene.index.Term;
import org.jsoup.Jsoup;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This the GraphQL API for the new joshlong.com. Most of the endpoints are GraphQL, with
 * the exception of a few endpoints intended to simplify integration, like one for
 * Github's webhooks.
 * <p>
 * It listens for webhooks from Github to know when to download and re-index the html
 * pages with a Spring Batch job.
 * <p>
 * It supports searching the blog posts with an in-memory Lucene index.
 *
 * @author <a href="mailto:josh@joshlong.com">Josh Long</a>
 */

@EnableConfigurationProperties(BlogProperties.class)
@SpringBootApplication
public class SiteApplication {

	@Bean
	ApplicationRunner indexRunner(IndexService is) {
		return args -> {
			is.refreshIndex();
			is.search("content: \"india\"").forEach(b -> System.out.println(b.title()));
			is.search("title: \"Shanghai, December 4th\"").forEach(b -> System.out.println(b.title()));
		};
	}

	private static final Log log = LogFactory.getLog(DefaultBlogPostService.class);

	public static void main(String[] args) {
		SpringApplication.run(SiteApplication.class, args);
	}

}

@Controller
@ResponseBody
class SearchRestController {

	private final IndexService indexService;

	SearchRestController(IndexService indexService) {
		this.indexService = indexService;
	}

	@GetMapping("/search")
	Collection<BlogPost> search(@RequestParam String query) {
		return this.indexService.search(query);
	}

}

@ConstructorBinding
@ConfigurationProperties("blog")
record BlogProperties(Resource contentRootDirectoryResource, Resource outputDirectoryResource) {
}

@Configuration
class BlogConfiguration {

	@Bean
	@SneakyThrows
	IndexService indexService(ApplicationEventPublisher publisher, BlogProperties properties,
			BlogPostService blogPostService, LuceneTemplate luceneTemplate) {
		return new DefaultIndexService(publisher, properties.contentRootDirectoryResource().getFile(), blogPostService,
				luceneTemplate);
	}

	@Bean
	BlogPostService blogService(MarkdownService markdownService) {
		return new DefaultBlogPostService(markdownService);
	}

}

enum BlogPostContentType {

	HTML, MD

}

record BlogPost(String title, Date date, String originalContent, String processContent, boolean published,
		BlogPostContentType type) {
}

class DefaultBlogPostService implements BlogPostService {

	private final ThreadLocal<SimpleDateFormat> simpleDateFormatThreadLocal = new ThreadLocal<>();

	private final MarkdownService markdownService;

	DefaultBlogPostService(MarkdownService markdownService) {
		this.markdownService = markdownService;
	}

	@SneakyThrows
	@Override
	public BlogPost buildBlogPostFrom(BlogPostContentType type, Resource r) {
		return buildBlogPostFrom(type, r.getInputStream());
	}

	@SneakyThrows
	@Override
	public BlogPost buildBlogPostFrom(BlogPostContentType type, InputStream file) {
		try (var f = new InputStreamReader(file)) {
			var contents = FileCopyUtils.copyToString(f);
			return buildBlogPostFrom(type, contents);
		}
	}

	@SneakyThrows
	@Override
	public BlogPost buildBlogPostFrom(File file) {
		log.info("------");
		log.info("indexing " + file.getAbsolutePath());
		Assert.notNull(file, () -> "the file must not be null");
		Assert.state(file.exists(), () -> "the file " + file.getAbsolutePath() + " does not exist!");
		return buildBlogPostFrom(file.getName().toLowerCase(Locale.ROOT).endsWith(".md") ? BlogPostContentType.MD
				: BlogPostContentType.HTML, new FileInputStream(file));
	}

	private final Log log = LogFactory.getLog(getClass());

	@Override
	@SneakyThrows
	public BlogPost buildBlogPostFrom(BlogPostContentType type, String contents) {
		// Assert.state(contents.length() > 200, "the content should be 200 chars or
		// more");
		// log.info( contents .substring(0 , 200));

		var headerDivider = "~~~~~~";
		Assert.state(contents.contains(headerDivider), () -> "this blog  does not contain any headers! " + contents);
		var parts = contents.split(headerDivider);
		var header = buildHeader(parts[0]);
		var dateFromHeaderString = header.get("date");
		Assert.notNull(dateFromHeaderString, () -> "the blog must have a published date!");
		var date = buildHeaderDate(dateFromHeaderString);
		var processedContent = this.markdownService.convertMarkdownTemplateToHtml(parts[1]);
		return new BlogPost(header.get("title"), date, contents, processedContent,
				(header.get("status").toLowerCase(Locale.ROOT).equalsIgnoreCase("published")), type);
	}

	@SneakyThrows
	private Date buildHeaderDate(String date) {
		if (simpleDateFormatThreadLocal.get() == null) {
			simpleDateFormatThreadLocal.set(new SimpleDateFormat("y-M-d"));
		}
		Assert.state(3 == date.split("-").length, () -> "there should be 3 parts to the date");
		return simpleDateFormatThreadLocal.get().parse(date);
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

class DefaultIndexService implements IndexService {

	private final static Log log = LogFactory.getLog(DefaultIndexService.class);

	private final int maxResults = 1000;

	private final File root;

	private final Map<String, BlogPost> index = new ConcurrentHashMap<>();

	private final ApplicationEventPublisher publisher;

	private final BlogPostService blogPostService;

	private final Object monitor = new Object();

	private final LuceneTemplate luceneTemplate;

	private final Set<String> extensions = Arrays.stream(BlogPostContentType.values())
			.map(contentType -> contentType.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

	@SneakyThrows
	DefaultIndexService(ApplicationEventPublisher publisher, File contentRoot, BlogPostService blogPostService,
			LuceneTemplate luceneTemplate) {
		this.blogPostService = blogPostService;
		this.luceneTemplate = luceneTemplate;
		this.root = contentRoot;
		this.publisher = publisher;
	}

	@Override
	public Map<String, BlogPost> refreshIndex() {
		synchronized (this.monitor) {
			this.index.clear();
			this.index.putAll(this.buildIndex());
		}
		this.publisher.publishEvent(new IndexingFinishedEvent());
		return this.index;
	}

	@Override
	@SneakyThrows
	public Collection<BlogPost> search(String query) {
		log.info("searching for: " + query);
		var strings = searchIndex(query, this.maxResults);
		log.info("there are " + strings.size() + " results.");
		return strings.stream().map(this.index::get).collect(Collectors.toSet());
	}

	private List<String> searchIndex(String queryStr, int maxResults) throws Exception {
		return this.luceneTemplate.search(queryStr, maxResults, document -> document.get("path"));
	}

	@SneakyThrows
	private Map<String, BlogPost> buildIndex() {
		log.info("building index..");
		var mapOfContent = Files.walk(root.toPath()) //
				.parallel() //
				.map(Path::toFile) //
				.filter(this::isValidFile) //
				.collect(Collectors.toMap(file -> file.getAbsolutePath().substring(root.getAbsolutePath().length()),
						blogPostService::buildBlogPostFrom));

		this.luceneTemplate.write(mapOfContent.entrySet(), entry -> {
			var rp = entry.getKey();
			var bp = entry.getValue();
			var doc = buildBlogPost(rp, bp);
			return new DocumentWriteMapper.DocumentWrite(new Term("key", buildHashKeyFor(bp)), doc);
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
		document.add(new StringField("path", relativePath, Field.Store.YES));
		document.add(new TextField("originalContent", post.originalContent(), Field.Store.YES));
		document.add(new TextField("content", htmlToText(post.processContent()), Field.Store.YES));
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

}

interface IndexService {

	Map<String, BlogPost> refreshIndex();

	Collection<BlogPost> search(String query);

}

interface BlogPostService {

	BlogPost buildBlogPostFrom(BlogPostContentType type, String contents);

	BlogPost buildBlogPostFrom(BlogPostContentType type, Resource r);

	BlogPost buildBlogPostFrom(BlogPostContentType type, InputStream file);

	BlogPost buildBlogPostFrom(File file);

}

class IndexingFinishedEvent extends ApplicationEvent {

	public IndexingFinishedEvent() {
		super(new Date());
	}

}