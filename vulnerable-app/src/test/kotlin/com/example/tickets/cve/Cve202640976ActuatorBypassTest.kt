package com.example.tickets.cve

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort

/** Critical. Boot's default security authorizes nothing when Actuator is present without Health. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class Cve202640976ActuatorBypassTest(@LocalServerPort private val port: Int) {

    @Test
    fun `anonymous requests are rejected`() {
        val response = send("http://localhost:$port/report")

        assertThat(response.body())
            .describedAs("VULNERABLE to CVE-2026-40976: an anonymous request read a protected endpoint")
            .isNotEqualTo("tickets report")
        assertThat(response.statusCode())
            .describedAs("Unexpected response: ${response.body().take(160)}")
            .isEqualTo(401)
    }

    @Test
    fun `logged in requests are allowed`() {
        assertThat(send("http://localhost:$port/report", login = true).statusCode()).isEqualTo(200)
    }
}
