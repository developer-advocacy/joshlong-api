package com.joshlong.sitegenerator;

import com.joshlong.lucene.DocumentWriteMapper;
import com.joshlong.lucene.LuceneTemplate;
import com.joshlong.templates.MarkdownService;
import lombok.SneakyThrows;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.*;
import org.apache.lucene.index.Term;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.ReflectionUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
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

	public static void main(String[] args) {
		SpringApplication.run(SiteApplication.class, args);
	}

	private static final Log log = LogFactory.getLog(SiteApplication.class);

	@Bean
	ApplicationRunner runner(BlogProperties properties, IndexService indexService) {
		return args -> {
			var contentRoot = properties.contentRootDirectoryResource();
			var blogPosts = indexService.buildIndexFor(contentRoot.getFile());
			blogPosts.forEach((relativePath, bp) -> log.info("" + relativePath + "=" + bp.toString()));
		};
	}

}

@ConstructorBinding
@ConfigurationProperties("blog")
record BlogProperties(Resource contentRootDirectoryResource, Resource outputDirectoryResource) {
}

@Configuration
class BlogConfiguration {

	@Bean
	IndexService indexService(BlogPostService blogPostService, LuceneTemplate luceneTemplate) {
		return new DefaultIndexService(blogPostService, luceneTemplate);
	}

	@Bean
	BlogPostService blogService(MarkdownService markdownService) {
		return new DefaultBlogPostService(markdownService);
	}

}

enum BlogPostContentType {

	HTML, MARKDOWN

}

record BlogPost(String title, Date date, String originalContent, String processContent, boolean published,
		BlogPostContentType type) {
}

class DefaultBlogPostService implements BlogPostService {

	private static final Log log = LogFactory.getLog(DefaultBlogPostService.class);

	private final ThreadLocal<SimpleDateFormat> simpleDateFormatThreadLocal = new ThreadLocal<>();

	private final MarkdownService markdownService;

	DefaultBlogPostService(MarkdownService markdownService) {
		this.markdownService = markdownService;
	}

	private static void exception(Exception e) throws RuntimeException {
		log.error(NestedExceptionUtils.buildMessage("an exception has occurred! ", e));
		ReflectionUtils.rethrowRuntimeException(e);
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
		Assert.notNull(file, () -> "the file must not be null");
		Assert.state(file.exists(), () -> "the file " + file.getAbsolutePath() + " does not exist!");
		return buildBlogPostFrom(file.getName().toLowerCase(Locale.ROOT).endsWith(".md") ? BlogPostContentType.MARKDOWN
				: BlogPostContentType.HTML, new FileInputStream(file));
	}

	@Override
	@SneakyThrows
	public BlogPost buildBlogPostFrom(BlogPostContentType type, String contents) {
		var headerDivider = "~~~~~~";
		Assert.state(contents.contains(headerDivider), () -> "this blog does not contain any headers!");
		var parts = contents.split(headerDivider);
		var header = buildHeader(parts[0]);
		var dateFromHeaderString = header.get("date");
		Assert.notNull(dateFromHeaderString, () -> "the blog must have a published date!");
		var date = buildHeaderDate(dateFromHeaderString);
		var processedContent = this.markdownService.convertMarkdownTemplateToHtml(parts[1]);
		return new BlogPost(

				header.get("title"), date, contents, processedContent,
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

	private final BlogPostService blogPostService;

	private final LuceneTemplate luceneTemplate;

	private final Set<String> extensions = Arrays.stream(BlogPostContentType.values())
			.map(bpct -> bpct.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

	DefaultIndexService(BlogPostService blogPostService, LuceneTemplate luceneTemplate) {
		this.blogPostService = blogPostService;
		this.luceneTemplate = luceneTemplate;
	}

	@SneakyThrows
	@Override
	public Map<String, BlogPost> buildIndexFor(File root) {

		var mapOfContent = Files.walk(root.toPath()).parallel().map(Path::toFile).filter(this::isValidFile)
				.collect(Collectors.toMap(file -> file.getAbsolutePath().substring(root.getAbsolutePath().length()),
						blogPostService::buildBlogPostFrom));

		this.luceneTemplate.write(mapOfContent.entrySet(), entry -> {
			var rp = entry.getKey();
			var bp = entry.getValue();
			var doc = buildPodcastDocument(rp, bp);
			return new DocumentWriteMapper.DocumentWrite(new Term("path", rp), doc);
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

	@SneakyThrows
	private Document buildPodcastDocument(String relativePath, BlogPost post) {
		var document = new Document();
		document.add(new StringField("title", post.title(), Field.Store.YES));
		document.add(new StringField("path", relativePath, Field.Store.YES));
		document.add(new TextField("originalContent", post.originalContent(), Field.Store.YES));
		document.add(new TextField("content", post.processContent(), Field.Store.YES));
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

	Map<String, BlogPost> buildIndexFor(File root);

}

interface BlogPostService {

	BlogPost buildBlogPostFrom(BlogPostContentType type, String contents);

	BlogPost buildBlogPostFrom(BlogPostContentType type, Resource r);

	BlogPost buildBlogPostFrom(BlogPostContentType type, InputStream file);

	BlogPost buildBlogPostFrom(File file);

}