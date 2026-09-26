package com.hanamizuki.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT setup: no session, no CSRF token, no login form.
 *
 * <p>Everything under /api is authenticated except the auth endpoints, the
 * health probe, and the public share surface — those are reached without a
 * login by design (03-detailed-design section 3.1).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final RestAuthEntryPoint entryPoint;

    public SecurityConfig(JwtFilter jwtFilter, RestAuthEntryPoint entryPoint) {
        this.jwtFilter = jwtFilter;
        this.entryPoint = entryPoint;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        // Public by design: getting a token, the health probe,
                        // and the share surface the LINE crawler fetches.
                        //
                        // Listed one by one rather than as /api/auth/** because
                        // /api/auth/me lives under the same prefix and does
                        // need a token — a wildcard here would leave its
                        // @AuthenticationPrincipal null.
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/s/**", "/og/**", "/api/share/**").permitAll()
                        // The SPA itself; Spring serves it in the demo build.
                        .requestMatchers("/", "/index.html", "/assets/**", "/photos/**", "/bg/**", "/favicon.svg").permitAll()
                        // Client-side routes, forwarded to index.html by SpaController.
                        // The screens authenticate through /api, not here.
                        .requestMatchers(HttpMethod.GET, "/me", "/camera", "/friends/**",
                                "/reunion/**", "/album/**", "/capsule/**").permitAll()
                        // Safe to leave open: the controllers behind this path
                        // are @Profile("dev"), so outside dev nothing is
                        // registered here and the path simply 404s.
                        .requestMatchers("/api/dev/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Cost 10, not the library default of 12. On a laptop the difference is a
     * few hundred milliseconds per login, which is noticeable when logging in
     * on stage.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
