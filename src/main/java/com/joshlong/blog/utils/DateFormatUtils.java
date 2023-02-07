package com.joshlong.blog.utils;

import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Dates. What even. Ammirite?
 */
@Slf4j
public abstract class DateFormatUtils {

	public static SimpleDateFormat getThreadsafeIsoDateTimeDateFormat() {
		return getIsoDateTimeDateFormat();
	}

	public static SimpleDateFormat getThreadSafeSimpleDateDateFormat() {
		return getSimpleDateDateFormat();
	}

	private static SimpleDateFormat getIsoDateTimeDateFormat() {
		/* Quoted "Z" to indicate UTC, no timezone offset */
		var tz = TimeZone.getTimeZone("UTC");
		var df = new DateFormatUtils.SiteSimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
		df.setTimeZone(tz);
		return df;
	}

	private static SimpleDateFormat getSimpleDateDateFormat() {
		return new DateFormatUtils.SiteSimpleDateFormat("y-M-d");
	}

	/*
	 * I want to use java.text.SimpleDateFormat.class directly in the proxies, but I get
	 * oddities related to modules, so this seems to be a workaround.
	 */
	public static class SiteSimpleDateFormat extends SimpleDateFormat {

		public SiteSimpleDateFormat(String pattern) {
			super(pattern);
		}

	}

}
