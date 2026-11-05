package com.example.tickets.cve

import com.example.tickets.Ticket
import com.example.tickets.TicketRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles

/** High. JSON Patch can rewrite `@Id`, so saving overwrites another user's record. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("api")
class Cve202647849JsonPatchIdentifierTest(
    @LocalServerPort private val port: Int,
    @Autowired private val tickets: TicketRepository,
) {

    @Test
    fun `a JSON Patch cannot overwrite another user's ticket`() {
        tickets.save(Ticket(id = 1, owner = "mallory", title = "mine"))
        tickets.save(Ticket(id = 2, owner = "alice", title = "theirs"))

        send(
            "http://localhost:$port/tickets/1",
            method = "PATCH",
            contentType = "application/json-patch+json",
            body = """[{"op":"replace","path":"/id","value":2}]""",
            login = true,
        )

        assertThat(tickets.findById(2).get().owner)
            .describedAs("VULNERABLE to CVE-2026-47849: a JSON Patch replaced another user's ticket")
            .isEqualTo("alice")
    }

    @Test
    fun `a JSON Patch can still change normal fields`() {
        tickets.save(Ticket(id = 3, owner = "alice", title = "before"))

        val response = send(
            "http://localhost:$port/tickets/3",
            method = "PATCH",
            contentType = "application/json-patch+json",
            body = """[{"op":"replace","path":"/title","value":"after"}]""",
            login = true,
        )

        assertThat(response.statusCode()).isBetween(200, 299)
        assertThat(tickets.findById(3).get().title).isEqualTo("after")
    }
}
