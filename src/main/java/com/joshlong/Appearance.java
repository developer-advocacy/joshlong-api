package com.joshlong;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public record Appearance(@JsonProperty("event") String event, @JsonProperty("start_date") Date startDate,
		@JsonProperty("end_date") Date endDate, @JsonProperty("time") String time,
		@JsonProperty("marketing_blurb") String marketingBlurb) {
}
