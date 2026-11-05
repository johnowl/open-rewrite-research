package com.example.tickets.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

/**
 * The `admin` profile: `/admin` needs a login and everything else is public.
 *
 * `securityMatcher(String)` with a builder bean carrying the servlet path is the condition for
 * CVE-2026-22753.
 */
@Configuration
@Profile("admin")
class AdminSecurityConfig {

    @Bean
    fun pathPatternRequestMatcherBuilder(): PathPatternRequestMatcher.Builder =
        PathPatternRequestMatcher.withDefaults().basePath("/api")

    @Bean
    @Order(1)
    fun adminChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/admin/**")
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .httpBasic(withDefaults())
            .build()

    @Bean
    @Order(2)
    fun publicChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}
