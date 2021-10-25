package com.joshlong.blog.dates;

import com.joshlong.blog.utils.DateFormatUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.DateFormat;

@Log4j2
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
