package com.joshlong.blog.appearances;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.blog.Appearance;
import com.joshlong.blog.AppearanceService;
import com.joshlong.blog.index.IndexingFinishedEvent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToLongFunction;

@Slf4j
class DefaultAppearanceService implements AppearanceService {

	private final Collection<Appearance> appearances = new CopyOnWriteArrayList<>();

	private final ZoneId defaultZoneId = ZoneId.systemDefault();

	private final TypeReference<Collection<JsonNode>> typeRef = new TypeReference<>() {
	};

	private final File appearancesRoot;

	private final ObjectMapper objectMapper;

	DefaultAppearanceService(File appearancesRoot, ObjectMapper objectMapper) throws Exception {
		this.appearancesRoot = appearancesRoot;
		this.objectMapper = objectMapper;
	}

	@EventListener(IndexingFinishedEvent.class)
	public void indexingFinishedEvent() throws Exception {
		var json = objectMapper.readValue(appearancesRoot, this.typeRef);
		synchronized (this.appearances) {
			this.appearances.clear();
			this.appearances.addAll(json.stream() //
					.map(this::buildAppearanceFrom)//
					.sorted(Comparator.comparingLong((ToLongFunction<Appearance>) value -> value.startDate().getTime())
							.reversed())//
					.toList());
		}
	}

	@Override
	public Collection<Appearance> getAppearances() {
		return this.appearances;
	}

	private Date buildDateFrom(String text) {
		var divider = "/";
		if (!(StringUtils.hasText(text) && text.contains(divider))) {
			return null;
		}
		var parts = text.split(divider);
		var month = Integer.parseInt(parts[0]);
		var date = Integer.parseInt(parts[1]);
		var year = Integer.parseInt(parts[2]);
		var localDate = LocalDate.of(year, month, date);
		return Date.from(localDate.atStartOfDay(this.defaultZoneId).toInstant());
	}

	@SneakyThrows
	private Appearance buildAppearanceFrom(JsonNode json) {

		if (log.isDebugEnabled())
			log.debug("the json node title is " + json.get("event") + " and the date were converting is "
					+ json.get("start_date"));

		var startDate = json.get("start_date");
		var endDate = json.get("end_date");
		var time = json.get("time");
		var event = json.get("event");
		var marketing_blurb = json.get("marketing_blurb");
		return new Appearance(event.textValue(), buildDateFrom(startDate.asText()), buildDateFrom(endDate.asText()),
				time.textValue(), marketing_blurb.textValue());
	}

}
