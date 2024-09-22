package com.joshlong.blogs;

import com.rometools.rome.feed.module.DCModuleImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import org.reflections.Reflections;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * registers hints for ATOM/RSS feed library ATOM.
 *
 * @author Josh Long
 */
class RomeHints implements RuntimeHintsRegistrar {

	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

		if (ClassUtils.isPresent("com.rometools.rome.feed.WireFeed", getClass().getClassLoader())) {

			var mcs = MemberCategory.values();

			var reflections = new Reflections("com.rometools.rome");
			reflections.getSubTypesOf(Serializable.class).forEach((cx) -> {
				hints.reflection().registerType(cx, mcs);
			});

			for (var c : new Class[] { Date.class, SyndEntry.class, DCModuleImpl.class }) {
				hints.reflection().registerType(c, mcs);
			}

			var resource = new ClassPathResource("/com/rometools/rome/rome.properties");
			hints.resources().registerResource(resource);

			try (var in = resource.getInputStream()) {
				var props = new Properties();
				props.load(in);
				props.propertyNames().asIterator().forEachRemaining(
						(pn) -> loadClasses((String) pn, props.getProperty((String) pn)).forEach((cn) -> {
							hints.reflection().registerType(TypeReference.of(cn), mcs);
						}));
			} //
			catch (Exception ex) {
				LoggerFactory.getLogger(getClass()).warn("got an exception loading the hints for ROME");
			}
		}
	}

	private static List<String> loadClasses(String propertyName, String propertyValue) {
		Assert.hasText(propertyName, "the propertyName must not be null");
		Assert.hasText(propertyValue, "the propertyValue must not be null");
		return Arrays.stream(propertyValue.contains(" ") ? propertyValue.split(" ") : new String[] { propertyValue })
				.map(String::trim).filter((xValue) -> !xValue.strip().equals("")).toList();
	}

}
