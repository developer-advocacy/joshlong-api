package com.joshlong.blog.utils.graalvm;

import org.springframework.nativex.hint.AotProxyHint;
import org.springframework.nativex.hint.ProxyBits;
import org.springframework.nativex.type.NativeConfiguration;

/**
 * The {@link com.joshlong.blog.utils.ThreadLocalUtils } class creates a proxy which runs
 * afoul of the GraalVM compiler.
 */
@AotProxyHint(targetClass = com.joshlong.blog.utils.DateFormatUtils.SiteSimpleDateFormat.class,
		proxyFeatures = ProxyBits.IS_STATIC)
public class DateFormatUtilsNativeConfiguration implements NativeConfiguration {

}
