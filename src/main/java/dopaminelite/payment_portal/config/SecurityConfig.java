package dopaminelite.payment_portal.config;

import dopaminelite.payment_portal.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for Payment Portal microservice.
 * 
 * This service uses JWT authentication. In production, tokens are issued by the User Service
 * and validated here. For development/testing, use the /api/v1/dev/auth/login endpoint to get tokens.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/dev/auth/**").permitAll() // Dev auth endpoints
                
                // Portal endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/portals/**").hasAnyRole("ADMIN", "STAFF", "STUDENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/portals/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/portals/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/portals/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/portals/**").hasRole("ADMIN")
                
                // Submission endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/portals/*/submissions").hasAnyRole("ADMIN", "STAFF", "STUDENT")
                .requestMatchers(HttpMethod.GET, "/api/v1/submissions/**").hasAnyRole("ADMIN", "STAFF", "STUDENT")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/submissions/*/status").hasAnyRole("ADMIN", "STAFF")
                
                // Data sheet endpoints
                .requestMatchers("/api/v1/data-sheets/**").hasAnyRole("ADMIN", "STAFF")
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
