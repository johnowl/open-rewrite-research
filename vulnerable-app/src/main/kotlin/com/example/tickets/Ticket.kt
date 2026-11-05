package com.example.tickets

import org.springframework.data.annotation.Id
import org.springframework.data.keyvalue.annotation.KeySpace
import org.springframework.data.repository.CrudRepository

/** A public setter on `id` is the condition for CVE-2026-47849. */
@KeySpace("tickets")
class Ticket(
    @Id var id: Long? = null,
    var owner: String = "",
    var title: String = "",
)

interface TicketRepository : CrudRepository<Ticket, Long>
