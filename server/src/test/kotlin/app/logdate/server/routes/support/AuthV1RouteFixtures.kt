package app.logdate.server.routes.support

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun googleAuthBody(
    idToken: String,
    requestedOwnerId: String = Uuid.random().toString(),
): String = """{"idToken":"$idToken","requestedOwnerId":"$requestedOwnerId"}"""

@OptIn(ExperimentalUuidApi::class)
fun signupPasskeyBeginBody(
    username: String,
    displayName: String,
): String =
    """
    {
      "username": "$username",
      "displayName": "$displayName",
      "requestedOwnerId": "${Uuid.random()}"
    }
    """.trimIndent()

fun signupPasskeyCompleteBody(
    sessionToken: String,
    credentialId: String,
    emailBindingToken: String? = null,
): String {
    val emailBinding =
        if (emailBindingToken == null) {
            ""
        } else {
            """
            ,
            "emailBinding": {
              "source": "google_id_token",
              "value": "$emailBindingToken"
            }
            """.trimIndent()
        }

    return (
        """
        {
          "sessionToken": "$sessionToken",
          "credential": {
            "id": "$credentialId",
            "rawId": "test-raw-id",
            "response": {
              "clientDataJSON": "test-client-data",
              "attestationObject": "test-attestation"
            },
            "type": "public-key"
          }$emailBinding
        }
        """.trimIndent()
    )
}

fun signinPasskeyBeginBody(username: String?): String =
    if (username == null) {
        "{}"
    } else {
        """{"username":"$username"}"""
    }

fun signinPasskeyCompleteBody(
    challenge: String,
    credentialId: String,
): String =
    """
    {
      "challenge": "$challenge",
      "credential": {
        "id": "$credentialId",
        "rawId": "$credentialId",
        "response": {
          "clientDataJSON": "test-client-data",
          "authenticatorData": "test-authenticator-data",
          "signature": "test-signature",
          "userHandle": "test-user-handle"
        },
        "type": "public-key"
      }
    }
    """.trimIndent()

fun addPasskeyCompleteBody(
    challenge: String,
    credentialId: String,
): String =
    """
    {
      "challenge": "$challenge",
      "credential": {
        "id": "$credentialId",
        "rawId": "test-raw-id",
        "response": {
          "clientDataJSON": "test-client-data",
          "attestationObject": "test-attestation"
        },
        "type": "public-key"
      }
    }
    """.trimIndent()

fun signupPasskeyCompleteBodyWithBindingSource(
    sessionToken: String,
    credentialId: String,
    source: String,
    bindingToken: String,
): String =
    """
    {
      "sessionToken": "$sessionToken",
      "credential": {
        "id": "$credentialId",
        "rawId": "test-raw-id",
        "response": {
          "clientDataJSON": "test-client-data",
          "attestationObject": "test-attestation"
        },
        "type": "public-key"
      },
      "emailBinding": {
        "source": "$source",
        "value": "$bindingToken"
      }
    }
    """.trimIndent()
