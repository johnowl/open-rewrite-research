package com.example.tickets

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.map.repository.config.EnableMapRepositories

@SpringBootApplication
@EnableMapRepositories
class TicketsApplication

fun main(args: Array<String>) {
    runApplication<TicketsApplication>(*args)
}
