package com.hanamizuki.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hanamizuki.backend.error.ErrorCode;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the bearer token and, if it checks out, populates the security context.
 *
 * <p>A bad token is not rejected here. The filter records why and lets the
 * request continue; if the endpoint turns out to be public it succeeds anyway,
 * and if it is not, {@link RestAuthEntryPoint} reports the recorded reason.
 * That separation is what keeps TOKEN_EXPIRED distinct from TOKEN_INVALID —
 * the frontend refreshes on the first and logs out on the second.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    static final String FAILURE_ATTRIBUTE = "jwtFailure";

    private final JwtIssuer issuer;

    public JwtFilter(JwtIssuer issuer) {
        this.issuer = issuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                AuthUser user = issuer.verify(token);
                var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                request.setAttribute(FAILURE_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(FAILURE_ATTRIBUTE, ErrorCode.TOKEN_INVALID);
            }
        }
        chain.doFilter(request, response);
    }
}
