package com.joshlong.utils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * imagine how nice a world without checked exceptions would be?
 */
public abstract class UrlUtils {

	public static URL url(String url) {
		try {
			return URI.create(url).toURL();
		} //
		catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

}
