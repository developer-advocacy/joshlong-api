package com.joshlong.videos.youtube.jobs;

import com.joshlong.videos.youtube.client.Playlist;

import java.util.Collection;

interface IngestJob extends Job<Collection<Playlist>> {

}
