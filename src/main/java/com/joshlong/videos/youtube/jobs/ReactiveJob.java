package com.joshlong.videos.youtube.jobs;

import org.reactivestreams.Publisher;

interface ReactiveJob<T> {

	Publisher<T> run();

}
