package com.example.tickets.cve

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles

/**
 * Critical. Spring's String converter sets Content-Length with `setHeader`, which the security
 * filter doesn't track, so the response commits before the security headers are written.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("api")
class Cve202622732MissingHeadersTest(@LocalServerPort private val port: Int) {

    @Test
    fun `security headers are written`() {
        val response = send("http://localhost:$port/report", login = true)

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("Cache-Control"))
            .describedAs("VULNERABLE to CVE-2026-22732: the response has no Cache-Control header")
            .isPresent
    }
}
