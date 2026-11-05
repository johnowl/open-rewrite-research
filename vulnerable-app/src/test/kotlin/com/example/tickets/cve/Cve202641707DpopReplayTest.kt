package com.example.tickets.cve

import com.example.tickets.security.DpopProofVerifier
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.JwtException
import java.util.Date
import java.util.UUID

/** High. Flooding the fixed-size `jti` cache evicts a used proof, which can then be replayed. */
class Cve202641707DpopReplayTest {
    private val key = ECKeyGenerator(Curve.P_256).generate()
    private val verifier = DpopProofVerifier()

    private fun proof(): String {
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("dpop+jwt"))
            .jwk(key.toPublicJWK())
            .build()
        val claims = JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .claim("htm", METHOD)
            .claim("htu", TARGET)
            .issueTime(Date())
            .build()
        return SignedJWT(header, claims).apply { sign(ECDSASigner(key)) }.serialize()
    }

    private fun verify(proof: String) = verifier.verify(proof, METHOD, TARGET)

    @Test
    fun `a proof cannot be replayed immediately`() {
        val stolen = proof()
        verify(stolen)

        assertThatThrownBy { verify(stolen) }.isInstanceOf(JwtException::class.java)
    }

    @Test
    fun `a proof cannot be replayed after flooding the cache`() {
        val stolen = proof()
        verify(stolen)

        repeat(FLOOD) { runCatching { verify(proof()) } }

        assertThat(runCatching { verify(stolen) }.exceptionOrNull())
            .describedAs("VULNERABLE to CVE-2026-41707: a stolen DPoP proof was accepted a second time")
            .isInstanceOf(JwtException::class.java)
    }

    private companion object {
        const val METHOD = "GET"
        const val TARGET = "https://tickets.example.com/report"
        const val FLOOD = 1_100
    }
}
