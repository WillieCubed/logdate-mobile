package app.logdate.server.passkeys

import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.statement.AttestationStatement
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement

/**
 * Build a webauthn4j [CredentialRecord] from the bits we actually persist server-side.
 *
 * webauthn4j's `CredentialRecordImpl` constructor takes ten positional arguments — most are
 * WebAuthn L2/L3 extension fields (`uvInitialized`, `backupEligible`, `backupState`,
 * `authenticatorExtensions`, `clientData`, `clientExtensions`, `transports`) that we don't
 * model in our schema today. Passing all of them as `null` at every call site is unreadable
 * and makes "the nullness is intentional" indistinguishable from "we forgot to wire something
 * up". Hiding the null blob behind this helper:
 *
 *  - keeps the auth-flow call sites focused on the three fields that actually matter
 *    (attested credential data, sign count, attestation statement);
 *  - gives us one place to start populating the L2/L3 fields if/when we choose to track them;
 *  - documents the deliberate omission once, instead of once per call site.
 */
internal fun credentialRecord(
    attestedCredentialData: AttestedCredentialData,
    signCount: Long,
    attestationStatement: AttestationStatement = NoneAttestationStatement(),
): CredentialRecord =
    CredentialRecordImpl(
        attestationStatement,
        // uvInitialized =
        null,
        // backupEligible =
        null,
        // backupState =
        null,
        signCount,
        attestedCredentialData,
        // authenticatorExtensions =
        null,
        // clientData =
        null,
        // clientExtensions =
        null,
        // transports =
        null,
    )
