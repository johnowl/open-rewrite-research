package com.example.tickets

import java.time.Duration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ReportController {

    @GetMapping("/report")
    fun report() = "tickets report"

    @GetMapping("/admin/report")
    fun adminReport() = "admin report"
}
