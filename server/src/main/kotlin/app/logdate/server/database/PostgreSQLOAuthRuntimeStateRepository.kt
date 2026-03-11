package app.logdate.server.database

import app.logdate.server.oauth.OAuthRuntimeStateRepository
import app.logdate.server.oauth.StoredAuthorizationCode
import app.logdate.server.oauth.StoredAuthorizationRequest
import app.logdate.server.oauth.StoredRefreshToken
import app.logdate.server.util.toKotlinInstant
import app.logdate.server.util.toKotlinxInstant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

internal class PostgreSQLOAuthRuntimeStateRepository : OAuthRuntimeStateRepository {
    override suspend fun saveAuthorizationRequest(request: StoredAuthorizationRequest): StoredAuthorizationRequest =
        transaction {
            val existing =
                OAuthAuthorizationRequestsTable
                    .selectAll()
                    .where { OAuthAuthorizationRequestsTable.requestUri eq request.requestUri }
                    .singleOrNull()

            if (existing == null) {
                OAuthAuthorizationRequestsTable.insert {
                    it[requestUri] = request.requestUri
                    it[clientId] = request.clientId
                    it[clientName] = request.clientName
                    it[redirectUri] = request.redirectUri
                    it[scope] = request.scope
                    it[state] = request.state
                    it[loginHint] = request.loginHint
                    it[codeChallenge] = request.codeChallenge
                    it[dpopKeyThumbprint] = request.dpopKeyThumbprint
                    it[clientAuthKeyId] = request.clientAuthKeyId
                    it[clientAuthKeyThumbprint] = request.clientAuthKeyThumbprint
                    it[expiresAt] = request.expiresAt.toKotlinxInstant()
                }
            } else {
                OAuthAuthorizationRequestsTable.update({ OAuthAuthorizationRequestsTable.requestUri eq request.requestUri }) {
                    it[clientId] = request.clientId
                    it[clientName] = request.clientName
                    it[redirectUri] = request.redirectUri
                    it[scope] = request.scope
                    it[state] = request.state
                    it[loginHint] = request.loginHint
                    it[codeChallenge] = request.codeChallenge
                    it[dpopKeyThumbprint] = request.dpopKeyThumbprint
                    it[clientAuthKeyId] = request.clientAuthKeyId
                    it[clientAuthKeyThumbprint] = request.clientAuthKeyThumbprint
                    it[expiresAt] = request.expiresAt.toKotlinxInstant()
                }
            }
            request
        }

    override suspend fun findAuthorizationRequest(requestUri: String): StoredAuthorizationRequest? =
        transaction {
            OAuthAuthorizationRequestsTable
                .selectAll()
                .where { OAuthAuthorizationRequestsTable.requestUri eq requestUri }
                .singleOrNull()
                ?.toAuthorizationRequestState()
        }

    override suspend fun deleteAuthorizationRequest(requestUri: String): Boolean =
        transaction {
            OAuthAuthorizationRequestsTable.deleteWhere { OAuthAuthorizationRequestsTable.requestUri eq requestUri } > 0
        }

    override suspend fun saveAuthorizationCode(code: StoredAuthorizationCode): StoredAuthorizationCode =
        transaction {
            val existing =
                OAuthAuthorizationCodesTable
                    .selectAll()
                    .where { OAuthAuthorizationCodesTable.code eq code.code }
                    .singleOrNull()

            if (existing == null) {
                OAuthAuthorizationCodesTable.insert {
                    it[OAuthAuthorizationCodesTable.code] = code.code
                    it[clientId] = code.clientId
                    it[redirectUri] = code.redirectUri
                    it[subjectDid] = code.subjectDid
                    it[subjectHandle] = code.subjectHandle
                    it[scope] = code.scope
                    it[codeChallenge] = code.codeChallenge
                    it[dpopKeyThumbprint] = code.dpopKeyThumbprint
                    it[clientAuthKeyId] = code.clientAuthKeyId
                    it[clientAuthKeyThumbprint] = code.clientAuthKeyThumbprint
                    it[expiresAt] = code.expiresAt.toKotlinxInstant()
                }
            } else {
                OAuthAuthorizationCodesTable.update({ OAuthAuthorizationCodesTable.code eq code.code }) {
                    it[clientId] = code.clientId
                    it[redirectUri] = code.redirectUri
                    it[subjectDid] = code.subjectDid
                    it[subjectHandle] = code.subjectHandle
                    it[scope] = code.scope
                    it[codeChallenge] = code.codeChallenge
                    it[dpopKeyThumbprint] = code.dpopKeyThumbprint
                    it[clientAuthKeyId] = code.clientAuthKeyId
                    it[clientAuthKeyThumbprint] = code.clientAuthKeyThumbprint
                    it[expiresAt] = code.expiresAt.toKotlinxInstant()
                }
            }
            code
        }

    override suspend fun takeAuthorizationCode(code: String): StoredAuthorizationCode? =
        transaction {
            val existing =
                OAuthAuthorizationCodesTable
                    .selectAll()
                    .where { OAuthAuthorizationCodesTable.code eq code }
                    .singleOrNull()
                    ?.toAuthorizationCodeState()
            if (existing != null) {
                OAuthAuthorizationCodesTable.deleteWhere { OAuthAuthorizationCodesTable.code eq code }
            }
            existing
        }

    override suspend fun saveRefreshToken(token: StoredRefreshToken): StoredRefreshToken =
        transaction {
            val existing =
                OAuthRefreshTokensTable
                    .selectAll()
                    .where { OAuthRefreshTokensTable.token eq token.token }
                    .singleOrNull()

            if (existing == null) {
                OAuthRefreshTokensTable.insert {
                    it[OAuthRefreshTokensTable.token] = token.token
                    it[clientId] = token.clientId
                    it[subjectDid] = token.subjectDid
                    it[scope] = token.scope
                    it[dpopKeyThumbprint] = token.dpopKeyThumbprint
                    it[clientAuthKeyId] = token.clientAuthKeyId
                    it[clientAuthKeyThumbprint] = token.clientAuthKeyThumbprint
                    it[expiresAt] = token.expiresAt.toKotlinxInstant()
                    it[revokedAt] = token.revokedAt?.toKotlinxInstant()
                }
            } else {
                OAuthRefreshTokensTable.update({ OAuthRefreshTokensTable.token eq token.token }) {
                    it[clientId] = token.clientId
                    it[subjectDid] = token.subjectDid
                    it[scope] = token.scope
                    it[dpopKeyThumbprint] = token.dpopKeyThumbprint
                    it[clientAuthKeyId] = token.clientAuthKeyId
                    it[clientAuthKeyThumbprint] = token.clientAuthKeyThumbprint
                    it[expiresAt] = token.expiresAt.toKotlinxInstant()
                    it[revokedAt] = token.revokedAt?.toKotlinxInstant()
                }
            }
            token
        }

    override suspend fun findRefreshToken(token: String): StoredRefreshToken? =
        transaction {
            OAuthRefreshTokensTable
                .selectAll()
                .where { OAuthRefreshTokensTable.token eq token }
                .singleOrNull()
                ?.toRefreshTokenState()
        }

    override suspend fun revokeRefreshToken(
        token: String,
        revokedAt: kotlin.time.Instant,
    ): Boolean =
        transaction {
            OAuthRefreshTokensTable.update({ OAuthRefreshTokensTable.token eq token }) {
                it[OAuthRefreshTokensTable.revokedAt] = revokedAt.toKotlinxInstant()
            } > 0
        }

    override suspend fun deleteRefreshToken(token: String): Boolean =
        transaction {
            OAuthRefreshTokensTable.deleteWhere { OAuthRefreshTokensTable.token eq token } > 0
        }
}

private fun ResultRow.toAuthorizationRequestState(): StoredAuthorizationRequest =
    StoredAuthorizationRequest(
        requestUri = this[OAuthAuthorizationRequestsTable.requestUri],
        clientId = this[OAuthAuthorizationRequestsTable.clientId],
        clientName = this[OAuthAuthorizationRequestsTable.clientName],
        redirectUri = this[OAuthAuthorizationRequestsTable.redirectUri],
        scope = this[OAuthAuthorizationRequestsTable.scope],
        state = this[OAuthAuthorizationRequestsTable.state],
        loginHint = this[OAuthAuthorizationRequestsTable.loginHint],
        codeChallenge = this[OAuthAuthorizationRequestsTable.codeChallenge],
        dpopKeyThumbprint = this[OAuthAuthorizationRequestsTable.dpopKeyThumbprint],
        clientAuthKeyId = this[OAuthAuthorizationRequestsTable.clientAuthKeyId],
        clientAuthKeyThumbprint = this[OAuthAuthorizationRequestsTable.clientAuthKeyThumbprint],
        expiresAt = this[OAuthAuthorizationRequestsTable.expiresAt].toKotlinInstant(),
    )

private fun ResultRow.toAuthorizationCodeState(): StoredAuthorizationCode =
    StoredAuthorizationCode(
        code = this[OAuthAuthorizationCodesTable.code],
        clientId = this[OAuthAuthorizationCodesTable.clientId],
        redirectUri = this[OAuthAuthorizationCodesTable.redirectUri],
        subjectDid = this[OAuthAuthorizationCodesTable.subjectDid],
        subjectHandle = this[OAuthAuthorizationCodesTable.subjectHandle],
        scope = this[OAuthAuthorizationCodesTable.scope],
        codeChallenge = this[OAuthAuthorizationCodesTable.codeChallenge],
        dpopKeyThumbprint = this[OAuthAuthorizationCodesTable.dpopKeyThumbprint],
        clientAuthKeyId = this[OAuthAuthorizationCodesTable.clientAuthKeyId],
        clientAuthKeyThumbprint = this[OAuthAuthorizationCodesTable.clientAuthKeyThumbprint],
        expiresAt = this[OAuthAuthorizationCodesTable.expiresAt].toKotlinInstant(),
    )

private fun ResultRow.toRefreshTokenState(): StoredRefreshToken =
    StoredRefreshToken(
        token = this[OAuthRefreshTokensTable.token],
        clientId = this[OAuthRefreshTokensTable.clientId],
        subjectDid = this[OAuthRefreshTokensTable.subjectDid],
        scope = this[OAuthRefreshTokensTable.scope],
        dpopKeyThumbprint = this[OAuthRefreshTokensTable.dpopKeyThumbprint],
        clientAuthKeyId = this[OAuthRefreshTokensTable.clientAuthKeyId],
        clientAuthKeyThumbprint = this[OAuthRefreshTokensTable.clientAuthKeyThumbprint],
        expiresAt = this[OAuthRefreshTokensTable.expiresAt].toKotlinInstant(),
        revokedAt = this[OAuthRefreshTokensTable.revokedAt]?.toKotlinInstant(),
    )
