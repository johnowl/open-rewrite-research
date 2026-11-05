package com.example.tickets.cve

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.util.Base64

/** A real HTTP client, so requests pass through the real filter chain and servlet container. */
internal fun send(
    url: String,
    method: String = "GET",
    body: String? = null,
    contentType: String? = null,
    login: Boolean = false,
): HttpResponse<String> {
    val request = HttpRequest.newBuilder(URI.create(url))
        .method(method, body?.let { BodyPublishers.ofString(it) } ?: BodyPublishers.noBody())
    contentType?.let { request.header("Content-Type", it) }
    if (login) {
        val credentials = Base64.getEncoder().encodeToString("demo:demo-password".toByteArray())
        request.header("Authorization", "Basic $credentials")
    }
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString())
}
