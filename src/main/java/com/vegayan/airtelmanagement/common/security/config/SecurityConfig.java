package com.vegayan.airtelmanagement.common.security.config;



import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Keep in sync with JwtAuthenticationFilter.shouldNotFilter.
                        // /auth/v1/logout is deliberately NOT here any more: it
                        // ends a session, so it must be able to prove whose.
                        .requestMatchers(PublicEndpoints.PATTERNS)
                        .permitAll()
                        .anyRequest().authenticated()
                )
                // Normalizes the "no token sent at all" case (which Spring
                // would otherwise answer with its default HTML/JSON error
                // page) to the same ApiResponse JSON shape JwtAuthenticationFilter
                // already uses for "token present but invalid/expired".
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(new ApiResponse("Fail", message)));
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(

                // Production - Airtel LB
//                "https://chngmgmt.airtel.com:3011",
//                "http://10.240.129.101:3011",
//                "http://10.240.129.101:443",
//                "https://chngmgmt.airtel.com:443",

                "https://chngmgmt.airtel.com",

                // Development / internal environments
                "http://192.168.0.156:3011",
                "http://10.240.129.36:3011",
                "http://10.240.129.37:3011",
                "http://192.168.1.8:5173",
                "http://192.168.1.8:5174",
                "http://192.168.1.8:5175",
                "http://192.168.0.29:5178",
                "http://192.168.0.29:5174",
                "http://192.168.0.50:5174",
                "http://192.168.0.50:5173",
                "http://192.168.0.107:5173",
                "http://192.168.1.36:5173",
                "http://192.168.1.36:5174",
                "http://172.20.10.2:5173",
                "http://172.20.10.2:5174",
                "http://192.168.0.22:5174",
                "http://192.168.0.107:5174",
                "http://192.168.0.106:5174"

        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        config.setAllowedHeaders(List.of("Authorization", "Content-Type","Cookie"));

        config.setExposedHeaders(List.of("Authorization","Set-Cookie"));

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

}
