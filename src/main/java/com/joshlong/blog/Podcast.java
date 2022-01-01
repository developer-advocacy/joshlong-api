package com.joshlong.blog;

import java.net.URL;
import java.util.Date;

public record Podcast(Integer id, String uid, String title, Date date, URL episodePhotoUri, URL episodeUri,
		String description) {
}
