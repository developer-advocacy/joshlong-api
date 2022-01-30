package com.joshlong.blog;

import lombok.extern.slf4j.Slf4j;

import java.net.URL;

@Slf4j
public record Content(String title, String html, URL imageUrl) {
}
