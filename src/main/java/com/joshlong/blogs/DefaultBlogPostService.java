package com.joshlong.blogs;

import com.joshlong.BlogPost;
import com.joshlong.BlogPostContentType;
import com.joshlong.BlogPostService;
import com.joshlong.templates.MarkdownService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import java.io.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
class DefaultBlogPostService implements BlogPostService {

	private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final MarkdownService markdownService;

	private final String apiRoot;

	DefaultBlogPostService(MarkdownService markdownService, String apiRoot) {
		this.markdownService = markdownService;
		this.apiRoot = apiRoot;
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
		var docuemnt = Jsoup.parse(html);
		var imgs = docuemnt.getElementsByTag("img");
		for (var i : imgs) {
			var ogSrc = i.attr("src");
			var src = (ogSrc + "").trim();
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
		if (ps != null && !ps.isEmpty()) {
			ps.forEach(element -> results.add(element.text()));
		}
		var list = results.stream().limit(countOfParagraphs).collect(Collectors.toList());
		return new PreviewParagraphsResults(list, results.size() > countOfParagraphs);
	}

	@SneakyThrows
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

	@SneakyThrows
	private Date buildHeaderDate(String date) {
		try {

			var dtf = dateTimeFormatter;
			var ld = LocalDate.parse(date, dtf);
			return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
		} //
		catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
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

	private record PreviewParagraphsResults(List<String> results, boolean truncated) {
	}

}
