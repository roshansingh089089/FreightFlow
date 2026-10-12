package com.freightflow.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.freightflow.auth.entity.AppUser;
import com.freightflow.auth.repository.UserRepo;
import com.freightflow.common.exception.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Value("${app.jwt-secret}")
  String secret;

  @Value("${app.frontend-origin}")
  String origin;

  @Value("${app.frontend-additional-origin:}")
  String additionalOrigin;

  @PostConstruct
  void validateSecret() {
    if (secret == null || secret.length() < 32)
      throw new IllegalStateException("JWT_SECRET must have at least 32 characters");
  }

  @Bean
  PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration c = new CorsConfiguration();
    List<String> allowedOrigins = new ArrayList<>(List.of(origin));
    if (!additionalOrigin.isBlank()) allowedOrigins.add(additionalOrigin);
    c.setAllowedOrigins(allowedOrigins);
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
    c.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }

  @Bean
  SecurityFilterChain chain(
      HttpSecurity h, UserRepo users, CorsConfigurationSource corsConfigurationSource)
      throws Exception {
    CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.setCookiePath("/");
    CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
    h.csrf(x -> x.csrfTokenRepository(csrf).csrfTokenRequestHandler(handler))
        .cors(x -> x.configurationSource(corsConfigurationSource))
        .sessionManagement(x -> x.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            x ->
                x.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf")
                    .permitAll()
                    .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/logout")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            x ->
                x.authenticationEntryPoint(
                    (request, response, exception) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .addFilterBefore(
            new OncePerRequestFilter() {
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                try {
                  Cookie[] cookies = req.getCookies();
                  if (cookies != null)
                    for (Cookie c : cookies)
                      if (c.getName().equals("ff_session")) {
                        UUID id =
                            UUID.fromString(
                                JWT.require(Algorithm.HMAC256(secret))
                                    .build()
                                    .verify(c.getValue())
                                    .getSubject());
                        users
                            .findById(id)
                            .filter(u -> u.getStatus().equals("ACTIVE"))
                            .ifPresent(
                                u ->
                                    SecurityContextHolder.getContext()
                                        .setAuthentication(
                                            new UsernamePasswordAuthenticationToken(
                                                u, null, List.of())));
                      }
                } catch (Exception ignored) {
                }
                chain.doFilter(req, res);
              }
            },
            UsernamePasswordAuthenticationFilter.class);
    return h.build();
  }

  public static AppUser current() {
    Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (!(p instanceof AppUser u)) throw new ApiException(401, "UNAUTHORIZED", "Please log in");
    return u;
  }
}
