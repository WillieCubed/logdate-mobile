package studio.hypertext.atproto.lexicon.generated.com.atproto.server

import kotlinx.serialization.Serializable

public object DescribeServerLexicon {
    public const val ID: String = "com.atproto.server.describeServer"
}

@Serializable
public data class DescribeServerOutput(
    /** List of domain suffixes that can be used in account handles. */
    val availableUserDomains: List<String>,
    /** Contact information */
    val contact: DescribeServerContact? = null,
    val did: String,
    /** If true, an invite code must be supplied to create an account on this instance. */
    val inviteCodeRequired: Boolean? = null,
    /** URLs of service policy documents. */
    val links: DescribeServerLinks? = null,
    /** If true, a phone verification token must be supplied to create an account on this instance. */
    val phoneVerificationRequired: Boolean? = null,
)

@Serializable
public data class DescribeServerContact(
    val email: String? = null,
)

@Serializable
public data class DescribeServerLinks(
    val privacyPolicy: String? = null,
    val termsOfService: String? = null,
)
