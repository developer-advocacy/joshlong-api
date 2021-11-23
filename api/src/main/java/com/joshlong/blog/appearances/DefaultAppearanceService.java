package com.joshlong.blog.appearances;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.blog.Appearance;
import com.joshlong.blog.AppearanceService;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Log4j2
class DefaultAppearanceService implements AppearanceService {

    private final Collection<Appearance> appearances;
    private final ZoneId defaultZoneId = ZoneId.systemDefault();
    private final TypeReference<Collection<JsonNode>> typeRef = new TypeReference<Collection<JsonNode>>() {
    };

    DefaultAppearanceService(File appearancesRoot, ObjectMapper objectMapper) throws Exception {
        var json = objectMapper.readValue(appearancesRoot, typeRef);
        this.appearances = json.stream().map(this::buildAppearanceFrom).collect(Collectors.toList());
    }

    @Override
    public Collection<Appearance> getAppearances() {
        return this.appearances;
    }

    private Date buildDateFrom(String text) {

        var divider = "/";

        var validText = StringUtils.hasText(text) && text.contains(divider);

        if (!validText)
            return null;


        var parts = text.split(divider);
        var month = Integer.parseInt(parts[0]);
        var date = Integer.parseInt(parts[1]);
        var year = Integer.parseInt(parts[2]);
        if (log.isDebugEnabled()) {
            log.debug("month:" + month + " date:" + date + " year:" + year);
        }
        var localDate = LocalDate.of(year, month, date);
        return Date.from(localDate.atStartOfDay(this.defaultZoneId).toInstant());

    }

    @SneakyThrows
    private Appearance buildAppearanceFrom(JsonNode json) {
        log.info("the json node title is " + json.get("event") + " and the date were converting is " + json.get("start_date"));
        var startDate = json.get("start_date");
        var endDate = json.get("end_date");
        var time = json.get("time");
        var event = json.get("event");
        var marketing_blurb = json.get("marketing_blurb");
        return new Appearance(event.textValue(),
                buildDateFrom(startDate.asText()), buildDateFrom(endDate.asText()), time.textValue(), marketing_blurb.textValue());
    }
}
