package com.freightflow;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;import java.util.*;import jakarta.annotation.PostConstruct;
@Configuration class SecurityConfig {
 @Value("${app.jwt-secret}") String secret; @Value("${app.frontend-origin}") String origin;
 @PostConstruct void validateSecret(){if(secret==null||secret.length()<32)throw new IllegalStateException("JWT_SECRET must have at least 32 characters");}
 @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
 @Bean CorsConfigurationSource corsConfigurationSource(){CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(List.of(origin));c.setAllowedMethods(List.of("GET","POST","PUT","OPTIONS"));c.setAllowedHeaders(List.of("Content-Type"));c.setAllowCredentials(true);UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;}
 @Bean SecurityFilterChain chain(HttpSecurity h,UserRepo users,CorsConfigurationSource corsConfigurationSource) throws Exception {h.csrf(x->x.disable()).cors(x->x.configurationSource(corsConfigurationSource)).sessionManagement(x->x.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(x->x.requestMatchers(HttpMethod.OPTIONS,"/**").permitAll().requestMatchers(HttpMethod.GET,"/api/health").permitAll().requestMatchers("/api/auth/signup","/api/auth/login","/api/auth/logout").permitAll().anyRequest().authenticated()).exceptionHandling(x->x.authenticationEntryPoint((request,response,exception)->response.sendError(HttpServletResponse.SC_UNAUTHORIZED))).addFilterBefore(new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{try{Cookie[] cookies=req.getCookies();if(cookies!=null)for(Cookie c:cookies)if(c.getName().equals("ff_session")){UUID id=UUID.fromString(JWT.require(Algorithm.HMAC256(secret)).build().verify(c.getValue()).getSubject());users.findById(id).filter(u->u.getStatus().equals("ACTIVE")).ifPresent(u->SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,List.of())));}}catch(Exception ignored){}chain.doFilter(req,res);}},UsernamePasswordAuthenticationFilter.class);return h.build();}
 static AppUser current(){Object p=SecurityContextHolder.getContext().getAuthentication().getPrincipal();if(!(p instanceof AppUser u))throw new ApiException(401,"UNAUTHORIZED","Please log in");return u;}
}
