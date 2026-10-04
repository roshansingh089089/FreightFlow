package com.freightflow;
import com.auth0.jwt.JWT;import com.auth0.jwt.algorithms.Algorithm;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.http.*;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;import java.util.*;
@RestController @RequestMapping("/api/auth") class AuthController {
 record Signup(@NotBlank String companyName,@NotBlank String fullName,@Email @NotBlank String email,@Size(min=8) String password,@NotBlank String country,String phone){}
 record Login(@Email String email,String password){}
 private final TenantRepo tenants;private final UserRepo users;private final PasswordEncoder encoder; @Value("${app.jwt-secret}") String secret;@Value("${app.cookie-secure}") boolean secure;
 AuthController(TenantRepo t,UserRepo u,PasswordEncoder e){tenants=t;users=u;encoder=e;}
 @PostMapping("/signup") @Transactional ResponseEntity<?> signup(@Valid @RequestBody Signup r,HttpServletResponse res){if(users.findByEmailIgnoreCase(r.email()).isPresent())throw new ApiException(409,"EMAIL_EXISTS","Email is already registered");Tenant t=new Tenant();t.setCompanyName(r.companyName().trim());t.setCountry(r.country());tenants.save(t);AppUser u=new AppUser();u.setTenantId(t.getId());u.setFullName(r.fullName().trim());u.setEmail(r.email().trim().toLowerCase());u.setPasswordHash(encoder.encode(r.password()));users.save(u);cookie(res,u,7);return ResponseEntity.status(201).body(profile(u,t));}
 @PostMapping("/login") ResponseEntity<?> login(@Valid @RequestBody Login r,HttpServletResponse res){AppUser u=users.findByEmailIgnoreCase(r.email()).orElseThrow(()->new ApiException(401,"INVALID_CREDENTIALS","Invalid email or password"));if(!encoder.matches(r.password(),u.getPasswordHash())||!u.getStatus().equals("ACTIVE"))throw new ApiException(401,"INVALID_CREDENTIALS","Invalid email or password");u.setLastLoginAt(Instant.now());users.save(u);cookie(res,u,7);return ResponseEntity.ok(profile(u,tenants.findById(u.getTenantId()).orElseThrow()));}
 @GetMapping("/me") Object me(){AppUser u=SecurityConfig.current();return profile(u,tenants.findById(u.getTenantId()).orElseThrow());}
 @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) void logout(HttpServletResponse res){ResponseCookie c=ResponseCookie.from("ff_session","").httpOnly(true).secure(secure).sameSite(secure?"None":"Lax").path("/").maxAge(Duration.ZERO).build();res.addHeader(HttpHeaders.SET_COOKIE,c.toString());}
 private void cookie(HttpServletResponse res,AppUser u,int days){String jwt=JWT.create().withSubject(u.getId().toString()).withExpiresAt(Date.from(Instant.now().plus(Duration.ofDays(days)))).sign(Algorithm.HMAC256(secret));res.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("ff_session",jwt).httpOnly(true).secure(secure).sameSite(secure?"None":"Lax").path("/").maxAge(Duration.ofDays(days)).build().toString());}
 private Object profile(AppUser u,Tenant t){return Map.of("id",u.getId(),"fullName",u.getFullName(),"email",u.getEmail(),"role",u.getRole(),"companyName",t.getCompanyName(),"country",t.getCountry());}
}
