package com.joshlong.podcasts;

import com.joshlong.index.IndexingFinishedEvent;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RomePodcastServiceTest {

    private final RomePodcastService service = new RomePodcastService();

    @Test
    void test() {
        this.service.onApplicationEvent(new IndexingFinishedEvent(Map.of(), new Date()));
        var podcasts = this.service.getPodcasts();
        assertNotNull(podcasts);
        assertFalse(podcasts.isEmpty());

    }
}