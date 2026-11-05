package com.example.tickets.security

import org.springframework.security.oauth2.jwt.DPoPProofContext
import org.springframework.security.oauth2.jwt.DPoPProofJwtDecoderFactory
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/** Verifies DPoP proofs, which should each be accepted only once. See CVE-2026-41707. */
@Component
class DpopProofVerifier {
    private val decoders = DPoPProofJwtDecoderFactory()

    fun verify(proof: String, method: String, targetUri: String): Jwt {
        val context = DPoPProofContext.withDPoPProof(proof).method(method).targetUri(targetUri).build()
        return decoders.createDecoder(context).decode(proof)
    }
}
