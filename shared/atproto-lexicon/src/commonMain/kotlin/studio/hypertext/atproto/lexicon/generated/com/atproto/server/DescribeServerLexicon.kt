package studio.hypertext.atproto.lexicon.generated.com.atproto.server

import kotlinx.serialization.Serializable

public object DescribeServerLexicon {
    public const val ID: String = "com.atproto.server.describeServer"
}

@Serializable
public data class DescribeServerOutput(
    val availableUserDomains: List<String>,
    val contact: DescribeServerContact?,
    val did: String,
    val inviteCodeRequired: Boolean?,
    val links: DescribeServerLinks?,
    val phoneVerificationRequired: Boolean?,
)

@Serializable
public data class DescribeServerContact(
    val email: String?,
)

@Serializable
public data class DescribeServerLinks(
    val privacyPolicy: String?,
    val termsOfService: String?,
)
