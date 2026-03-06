package app.logdate.server.routes.support

import app.logdate.server.auth.GoogleIdTokenClaims

fun googleClaims(
    subject: String,
    email: String,
    name: String,
    audience: String = "test-client",
): GoogleIdTokenClaims =
    GoogleIdTokenClaims(
        subject = subject,
        email = email,
        emailVerified = true,
        name = name,
        issuer = "https://accounts.google.com",
        audience = audience,
        expiresAtEpochSeconds = Long.MAX_VALUE,
        issuedAtEpochSeconds = 1L,
    )

fun googleClaimsByToken(vararg entries: Pair<String, GoogleIdTokenClaims>): Map<String, GoogleIdTokenClaims> = mapOf(*entries)
