package com.joshlong.videos.youtube.client;

import java.util.Date;

public record Channel(String channelId, String title, String description, Date publishedAt) {
}
