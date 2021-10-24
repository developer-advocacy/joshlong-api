package com.joshlong.sitegenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.joshlong.lucene.DocumentWriteMapper;
import com.joshlong.lucene.LuceneTemplate;
import com.joshlong.templates.MarkdownService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.eclipse.jgit.api.Git;
import org.jsoup.Jsoup;
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

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This the GraphQL API for the new joshlong.com. Most o the endpoints are
 * GraphQ the exception of a few endpoints intended to simplify integration,
 * like one for Github's webhooks.
 * <p>
 * It listens for webhooks from Github to know when to download and re-index the
 * html pages with a Spring Batch job.
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

@Controller
class ApiController {

    private final IndexService is;

    private final ThreadLocal<SimpleDateFormat> sdf = new ThreadLocal<>();

    ApiController(IndexService is) {
        this.is = is;
    }

    // blogPosts : [BlogPost]
    // blogPostByPath (path: String) : BlogPost

    @QueryMapping
    Collection<BlogPost> blogPosts() {
        return this.is.getIndex().values();
    }

    @QueryMapping
    Collection<BlogPost> blogPostByPath(@Argument String path) {
        var posts = blogPosts();
        return posts.stream().filter(bp -> bp.path().equals(path)).collect(Collectors.toList());
    }

    @MutationMapping
    Mono<String> rebuildIndex() {
        this.is.rebuildIndex();
        return Mono.just(getDateFormat().format(new Date()));
    }

    @QueryMapping
    Collection<BlogPost> search(@Argument String query) {
        return this.is.search(query);
    }

    @SchemaMapping(typeName = "BlogPost", field = "date")
    String date(BlogPost bp) {
        return getDateFormat().format(bp.date());
    }

    private SimpleDateFormat getDateFormat() {
        if (this.sdf.get() == null)
            this.sdf.set(buildSimpleDateFormat());
        return this.sdf.get();
    }

    private static SimpleDateFormat buildSimpleDateFormat() {
        var tz = TimeZone.getTimeZone("UTC");
        var df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        return df;
    }

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
    BlogPostService blogService(MarkdownService markdownService) {
        return new DefaultBlogPostService(markdownService);
    }

}

enum BlogPostContentType {
    HTML, MD
}

record BlogPost(String title, Date date, String originalContent, String processedContent, boolean published,
        BlogPostContentType type, String path) {
}

@Log4j2
class DefaultBlogPostService implements BlogPostService {

    private final ThreadLocal<SimpleDateFormat> simpleDateFormatThreadLocal = new ThreadLocal<>();

    private final MarkdownService markdownService;

    DefaultBlogPostService(MarkdownService markdownService) {
        this.markdownService = markdownService;
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
        log.info("------");
        log.info("indexing " + file.getAbsolutePath());
        Assert.notNull(file, () -> "the file must not be null");
        Assert.state(file.exists(), () -> "the file " + file.getAbsolutePath() + " does not exist!");
        var type = file.getName().toLowerCase(Locale.ROOT).endsWith(".md") ? BlogPostContentType.MD
                : BlogPostContentType.HTML;
        return buildBlogPostFrom(type, path, new FileInputStream(file));
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
        return new BlogPost(header.get("title"), date, contents, processedContent, published, type, path);
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

    private final Set<String> extensions = Arrays.stream(BlogPostContentType.values())
            .map(contentType -> contentType.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

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
    public Map<String, BlogPost> rebuildIndex() {

        this.ensureClonedRepository();

        synchronized (this.monitor) {
            this.index.clear();
            this.index.putAll(this.buildIndex());
        }
        this.publisher.publishEvent(new IndexingFinishedEvent());
        return this.index;
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

    private String computeRelativePath(File file, File contentDirectory) {
        var ext = ".md";
        var sub = file.getAbsolutePath().substring(contentDirectory.getAbsolutePath().length());
        if (sub.endsWith(ext))
            sub = sub.substring(0, sub.length() - ext.length()) + ".html"; 
        
        return sub;
    }

    @SneakyThrows
    private Map<String, BlogPost> buildIndex() {
        log.info("building index @ " + Instant.now() + '.');
        var contentDirectory = new File(this.root, "content");
        var mapOfContent = Files.walk(contentDirectory.toPath()) //
                .parallel() //
                .map(Path::toFile) //
                .filter(this::isValidFile) //
                .collect(Collectors.toMap(file -> computeRelativePath(file, contentDirectory),
                        file -> blogPostService.buildBlogPostFrom(computeRelativePath(file, contentDirectory), file)));

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

    Map<String, BlogPost> rebuildIndex();

    Collection<BlogPost> search(String query);

    Map<String, BlogPost> getIndex();

}

interface BlogPostService {

    // BlogPost buildBlogPostFrom(BlogPostContentType type, String path, String
    // contents);

    // BlogPost buildBlogPostFrom(BlogPostContentType type, Resource r);

    // BlogPost buildBlogPostFrom(BlogPostContentType type, InputStream file);

    BlogPost buildBlogPostFrom(String path, File file);

}

class IndexingFinishedEvent extends ApplicationEvent {

    public IndexingFinishedEvent() {
        super(new Date());
    }

}