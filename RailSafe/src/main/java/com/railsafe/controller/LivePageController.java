package com.railsafe.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the built React dashboard from the Spring Boot static folder.
 *
 * The Vite build writes to src/main/resources/static/live/. Spring's static
 * resource handler serves /live/index.html and its assets directly, but a
 * directory URL such as /live/ is not automatically resolved to index.html, so
 * these two mappings forward it explicitly. The existing static pages are
 * untouched.
 */
@Controller
public class LivePageController {

    @GetMapping({"/live", "/live/"})
    public String dashboard() {
        return "forward:/live/index.html";
    }
}
