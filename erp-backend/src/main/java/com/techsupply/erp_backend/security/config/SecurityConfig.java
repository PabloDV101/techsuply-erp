package com.techsupply.erp_backend.security.config;

import com.techsupply.erp_backend.security.infrastructure.security.OpaqueBearerAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import com.techsupply.erp_backend.security.api.AuthErrorWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    FilterRegistrationBean<OpaqueBearerAuthenticationFilter> bearerFilterRegistration(OpaqueBearerAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Only Spring Security owns this filter and its context lifecycle.
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, OpaqueBearerAuthenticationFilter tokenFilter, AuthErrorWriter errors) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, cause) ->
                    errors.write(response, HttpStatus.UNAUTHORIZED, "Autenticación requerida"))
                .accessDeniedHandler((request, response, cause) ->
                    errors.write(response, HttpStatus.FORBIDDEN, "Permiso insuficiente")))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
