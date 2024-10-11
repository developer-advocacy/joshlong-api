package com.joshlong.blogs;

import com.joshlong.BlogPost;
import com.joshlong.BlogPostContentType;
import com.joshlong.BlogPostService;
import com.joshlong.templates.MarkdownService;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import java.io.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

class DefaultBlogPostService implements BlogPostService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final MarkdownService markdownService;

	private final String apiRoot;

	DefaultBlogPostService(MarkdownService markdownService, String apiRoot) {
		this.markdownService = markdownService;
		this.apiRoot = apiRoot;
	}

	private BlogPost buildBlogPostFrom(BlogPostContentType type, String path, InputStream file) throws IOException {
		try (var f = new InputStreamReader(file)) {
			var contents = FileCopyUtils.copyToString(f);
			return buildBlogPostFrom(type, path, contents);
		}
	}

	@Override
	public BlogPost buildBlogPostFrom(String path, File file) {
		try {
			if (log.isDebugEnabled()) {
				log.debug("------");
				log.debug("indexing {} with path {}", file.getAbsolutePath(), path);
			}
			Assert.notNull(file, () -> "the file must not be null");
			Assert.state(file.exists(), () -> "the file " + file.getAbsolutePath() + " does not exist!");
			var type = file.getName().toLowerCase(Locale.ROOT).endsWith(".md") ? BlogPostContentType.MD
					: BlogPostContentType.HTML;
			return buildBlogPostFrom(type, path, new FileInputStream(file));
		} //
		catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	/*
	 * Given the HTML, we can use JSoup to discover the source attributes for all images
	 * in the markup
	 */
	private List<String> discoverImages(String html) {
		var results = new ArrayList<String>();
		var document = Jsoup.parse(html);
		var images = document.getElementsByTag("img");
		if (!images.isEmpty()) {
			images.forEach(element -> {
				if (element.hasAttr("src"))
					results.add(element.attr("src"));
			});
		}
		return results;
	}

	/**
	 * The images used to be hosted on joshlong.com/media/*, but now they're served off of
	 * some other URI, like api.joshlong.com/media, or maybe some other port. Either way,
	 * we need to make sure that the images are correctly resolved in the new world.
	 * @param resolveMediaServerUri the new API endpoint serving the responses
	 * @param localImagePrefixToMatch the pattern we will match in the HTML markup to
	 * detect old file paths
	 * @param html the HTML contents we want to change
	 * @return the resolved images
	 */
	private String resolveImageSources(String resolveMediaServerUri, String localImagePrefixToMatch, String html) {

		if (resolveMediaServerUri.endsWith("/"))
			resolveMediaServerUri = resolveMediaServerUri.substring(0, resolveMediaServerUri.length() - 1);

		var newHtml = html;
		var document = Jsoup.parse(html);
		var imageTags = document.getElementsByTag("img");
		for (var i : imageTags) {
			var ogSrc = i.attr("src");
			var src = (ogSrc).trim();
			if (src.startsWith(localImagePrefixToMatch)) {
				var newSrc = resolveMediaServerUri + src;
				newHtml = StringUtils.replace(newHtml, ogSrc, newSrc);
			}
		}
		return newHtml;
	}

	private PreviewParagraphsResults discoverPreviewParagraphs(String html, int countOfParagraphs) {
		var document = Jsoup.parse(html);
		var ps = document.getElementsByTag("p");
		var results = new ArrayList<String>();
		if (!ps.isEmpty()) {
			ps.forEach(element -> results.add(element.text()));
		}
		var list = results.stream().limit(countOfParagraphs).collect(Collectors.toList());
		return new PreviewParagraphsResults(list, results.size() > countOfParagraphs);
	}

	private BlogPost buildBlogPostFrom(BlogPostContentType type, String path, String contents) {
		var headerDivider = "~~~~~~";
		Assert.state(contents.contains(headerDivider), () -> "this blog  does not contain any headers! " + contents);
		var parts = contents.split(headerDivider);
		var header = buildHeader(parts[0]);
		var listed = Boolean.parseBoolean(header.getOrDefault("listed", "true"));
		var dateFromHeaderString = header.get("date");
		Assert.notNull(dateFromHeaderString, () -> "the blog must have a published date!");
		var date = buildHeaderDate(dateFromHeaderString);
		var processedContent = resolveImageSources(this.apiRoot, "/media/",
				this.markdownService.convertMarkdownTemplateToHtml(parts[1]));
		var published = header.get("status").toLowerCase(Locale.ROOT).equalsIgnoreCase("published");
		var images = discoverImages(processedContent);
		var heroParagraphs = discoverPreviewParagraphs(processedContent, 1);
		var uniquePath = path.toLowerCase(Locale.ROOT).startsWith("/jl/blogpost/")
				? path.substring("/jl/blogpost/".length()) : path;
		return new BlogPost(header.get("title"), date, contents, processedContent, published, type, path, uniquePath,
				images, heroParagraphs.results(), heroParagraphs.truncated(), listed);
	}

	private Date buildHeaderDate(String date) {
		try {
			var ld = LocalDate.parse(date, dateTimeFormatter);
			return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
		} //
		catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
	}

	private Map<String, String> buildHeader(String header) {
		try (var sw = new StringReader(header)) {
			var props = new Properties();
			props.load(sw);
			var map = new HashMap<String, String>();
			for (var key : props.keySet())
				map.put((String) key, (props.getProperty((String) key) + "").trim());
			return map;
		} //
		catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
	}

	private record PreviewParagraphsResults(List<String> results, boolean truncated) {
	}

}
