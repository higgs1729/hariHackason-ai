package com.hanamizuki.backend.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the built SPA for its client-side routes in the demo build
 * (02-basic-design §1.2).
 *
 * <p>A reload on {@code /album/3} asks the server for that path; without this
 * it is a 404. The list mirrors {@code frontend/src/routes.ts} rather than
 * catching everything, so an unknown {@code /api/...} path still gets the JSON
 * 404 and {@code /s/{token}} stays server-rendered.
 */
@Controller
public class SpaController {

    @GetMapping({"/me", "/camera", "/friends/**", "/reunion/**", "/album/**", "/capsule/**"})
    public String index() {
        return "forward:/index.html";
    }
}
