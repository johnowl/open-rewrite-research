package com.example.tickets.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/** The `api` profile: a stateless API behind HTTP Basic. */
@Configuration
@Profile("api")
class ApiSecurityConfig {

    @Bean
    fun apiChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .httpBasic(withDefaults())
            .csrf { it.disable() }
            .build()
}
