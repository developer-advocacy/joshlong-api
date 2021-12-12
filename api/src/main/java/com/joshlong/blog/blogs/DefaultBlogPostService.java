package com.joshlong.blog.blogs;

import com.joshlong.blog.BlogPost;
import com.joshlong.blog.BlogPostContentType;
import com.joshlong.blog.BlogPostService;
import com.joshlong.templates.MarkdownService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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

	private List<String> discoverPreviewParagraphs(String html, int countOfParagraphs) {
		var document = Jsoup.parse(html);
		var ps = document.getElementsByTag("p");
		var results = new ArrayList<String>();
		if (ps != null && ps.size() > 0) {
			ps.forEach(element -> results.add(element.text()));
		}
		return results.stream().limit(countOfParagraphs).collect(Collectors.toList());
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
		var firstParagraph = discoverPreviewParagraphs(processedContent, 2);
		return new BlogPost(header.get("title"), date, contents, processedContent, published, type, path, images,
				firstParagraph);
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
