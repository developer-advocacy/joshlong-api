package com.joshlong.blog.springtips;

import java.net.URI;
import java.util.Date;

/***
 * wraps the data coming <a href="https://springtipslive.io/episodes.json">from the
 * episode feed</a>
 *
 */
public record SpringTipsEpisode(URI blogUrl, Date date, int seasonNumber, String title, URI youtubeEmbedUrl,
		String youtubeId) {
}