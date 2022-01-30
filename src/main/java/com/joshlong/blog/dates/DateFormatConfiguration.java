package com.joshlong.blog.dates;

import com.joshlong.blog.utils.DateFormatUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.DateFormat;

@Configuration
class DateFormatConfiguration {

	@Bean
	@IsoDateFormat
	DateFormat isoDateFormat() {
		return DateFormatUtils.getThreadsafeIsoDateTimeDateFormat();
	}

	@Bean
	@SimpleDateDateFormat
	DateFormat simpleDateDateFormat() {
		return DateFormatUtils.getThreadSafeSimpleDateDateFormat();
	}

}
