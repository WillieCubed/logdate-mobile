package app.logdate.server.routes.support

fun googleAuthBody(idToken: String): String = """{"idToken":"$idToken"}"""

fun signupPasskeyBeginBody(
    username: String,
    displayName: String,
): String =
    """
    {
      "username": "$username",
      "displayName": "$displayName"
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
