package com.joshlong.blog.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.function.Function;

public abstract class JsonUtils {

    public static String valueOrNull(JsonNode node, String attr) {
        return valueOrNull(node, attr, i -> i);
    }

    public static <T> T valueOrNull(JsonNode node, String attribute, Function<String, T> transformer) {
        var v = node.get(attribute);
        if (v != null) {
            var s = v.textValue();
            if (StringUtils.hasText(s)) {
                return transformer.apply(s);
            }
        }
        return null;
    }
}
