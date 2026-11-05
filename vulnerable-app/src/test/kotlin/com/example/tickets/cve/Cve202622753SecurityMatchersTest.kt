package com.example.tickets.cve

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles

/** High. `securityMatchers` ignores the servlet path, so the admin chain never runs. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("admin")
class Cve202622753SecurityMatchersTest(@LocalServerPort private val port: Int) {

    @Test
    fun `the admin endpoint needs a login`() {
        val response = send("http://localhost:$port/api/admin/report")

        assertThat(response.statusCode())
            .describedAs("VULNERABLE to CVE-2026-22753: an anonymous request read the admin endpoint")
            .isEqualTo(401)
    }

    @Test
    fun `public endpoints stay public`() {
        assertThat(send("http://localhost:$port/api/report").statusCode()).isEqualTo(200)
    }
}
