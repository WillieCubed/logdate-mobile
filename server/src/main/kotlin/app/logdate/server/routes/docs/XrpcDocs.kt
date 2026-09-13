package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.ErrorEnvelope
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.bearerUnauthorized
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.pdsError
import app.logdate.server.routes.publicOperation
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode
import studio.hypertext.atproto.pds.CreateAccountRequest
import studio.hypertext.atproto.pds.CreateSessionRequest
import studio.hypertext.atproto.pds.DescribeRepoResponse
import studio.hypertext.atproto.pds.DescribeServerResponse
import studio.hypertext.atproto.pds.EmptyPdsResponse
import studio.hypertext.atproto.pds.ResolveHandleResponse
import studio.hypertext.atproto.pds.SessionInfoResponse
import studio.hypertext.atproto.syntax.Nsid

/**
 * Documentation for the AT Protocol XRPC methods in `XrpcRoutes.kt`. Method names are lexicon IDs
 * (`com.atproto.repo.getRecord`); descriptions explain them for readers who have never used AT Protocol.
 */
internal object XrpcDocs {
    // ---- Identity and server ------------------------------------------------------------------

    val resolveHandle: RouteConfig.() -> Unit = {
        publicOperation(
            "resolveHandle",
            ApiTags.XRPC,
            "Resolve a handle",
            """
            `com.atproto.identity.resolveHandle`. Resolves a handle such as `willie.logdate.app` into the DID it
            currently points at. A handle is the human-readable name; the DID is the stable identifier
            everything else keys on, so most other methods want the DID.
            """,
        )
        request {
            queryParameter<String>("handle") {
                description = "The handle to resolve, without `@`."
                required = true
                example("Example") { value = DocExamples.HANDLE }
            }
        }
        response {
            ok("The handle's DID.", ResolveHandleResponse(did = did))
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase("InvalidRequest", "`handle` is missing.", "Handle is required"),
                ErrorCase("HandleNotFound", "No account here has that handle.", "Handle not found: willie.logdate.app"),
            )
        }
    }

    val describeServer: RouteConfig.() -> Unit = {
        publicOperation(
            "describeAtprotoServer",
            ApiTags.XRPC,
            "Describe the PDS",
            """
            `com.atproto.server.describeServer`. Returns the server's own DID, the handle domains it can issue,
            and whether account creation needs an invite code or a phone number (it needs neither here). The
            AT Protocol equivalent of **Describe this server**.
            """,
        )
        response {
            ok(
                "The PDS description.",
                DescribeServerResponse(
                    did = "did:web:cloud.logdate.app",
                    availableUserDomains = listOf("logdate.app"),
                    inviteCodeRequired = false,
                    phoneVerificationRequired = false,
                ),
            )
        }
    }

    val describeRepo: RouteConfig.() -> Unit = {
        publicOperation(
            "describeRepo",
            ApiTags.XRPC,
            "Describe a repository",
            """
            `com.atproto.repo.describeRepo`. Returns a repository's handle, DID, DID document and the record
            collections it contains. A *repository* is one person's signed, content-addressed store of records;
            LogDate keeps entries, journals, links and drafts in it under the `studio.hypertext.logdate.*`
            collections.
            """,
        )
        request { repoParameter() }
        response {
            ok(
                "The repository's description.",
                DescribeRepoResponse(
                    handle = DocExamples.HANDLE,
                    did = did,
                    didDoc = IdentityDocs.didDocumentExample,
                    collections =
                        listOf(
                            Nsid.require("studio.hypertext.logdate.content"),
                            Nsid.require("studio.hypertext.logdate.journal"),
                            Nsid.require("studio.hypertext.logdate.association"),
                            Nsid.require("studio.hypertext.logdate.draft"),
                        ),
                    handleIsCorrect = true,
                ),
            )
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase("InvalidRequest", "`repo` is missing.", "Repo is required"),
                ErrorCase("RepoNotFound", "No repository here matches that DID or handle.", "Repo not found: willie.logdate.app"),
            )
        }
    }

    // ---- Sessions --------------------------------------------------------------------------------

    val createAccount: RouteConfig.() -> Unit = {
        publicOperation(
            "createAtprotoAccount",
            ApiTags.XRPC,
            "Create an AT Protocol account",
            """
            `com.atproto.server.createAccount`. Creates an account with a handle and password the AT Protocol
            way, and returns a session. LogDate's own apps use passkeys and **Authentication** instead; this
            exists so generic AT Protocol clients can sign up. The handle must be under one of the domains
            **Describe the PDS** lists.
            """,
        )
        request {
            jsonBody(
                CreateAccountRequest(email = DocExamples.EMAIL, handle = DocExamples.HANDLE, password = "correct horse battery staple"),
                "Handle, password and optional email. Invite codes and phone verification are not used here.",
            )
        }
        response {
            ok("The account exists and a session is open.", session)
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase("UnsupportedDomain", "The handle is not under a domain this server issues.", "Handle domain is not supported"),
                ErrorCase(
                    "InvalidPassword",
                    "The password does not meet the server's requirements.",
                    "Password does not satisfy server requirements",
                ),
                ErrorCase(
                    "InvalidRequest",
                    "The handle is taken or a field failed validation; the `message` says which. A body that is not valid JSON for this method answers `400` with an empty body instead.",
                    "Invalid session request",
                ),
            )
            pdsError(HttpStatusCode.Unauthorized, ErrorCase("InvalidToken", "The credentials were rejected.", "Authentication failed"))
            pdsError(HttpStatusCode.Forbidden, accountTakedown)
            notConfigured()
        }
    }

    val createSession: RouteConfig.() -> Unit = {
        publicOperation(
            "createAtprotoSession",
            ApiTags.XRPC,
            "Sign in with a password",
            """
            `com.atproto.server.createSession`. Signs in with a handle (or DID) and password and returns an
            `accessJwt` for requests and a `refreshJwt` for **Refresh the session**. This is AT Protocol's
            password login for generic clients; LogDate apps use passkeys.
            """,
        )
        request {
            jsonBody(
                CreateSessionRequest(identifier = DocExamples.HANDLE, password = "correct horse battery staple"),
                "Handle or DID, and password.",
            )
        }
        response {
            ok("Signed in. Send `accessJwt` as `Authorization: Bearer`.", session)
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "InvalidRequest",
                    "The identifier or password could not be processed. A body that is not valid JSON for this method answers `400` with an empty body instead.",
                    "Invalid session request",
                ),
            )
            pdsError(HttpStatusCode.Unauthorized, ErrorCase("InvalidToken", "Wrong handle or password.", "Authentication failed"))
            pdsError(HttpStatusCode.Forbidden, accountTakedown)
            notConfigured()
        }
    }

    val getSession: RouteConfig.() -> Unit = {
        bearerOperation(
            "getAtprotoSession",
            ApiTags.XRPC,
            "Get the current session",
            """
            `com.atproto.server.getSession`. Returns the account behind an `accessJwt` sent as
            `Authorization: Bearer`. Use it to check whether a stored session is still valid.
            """,
        )
        response {
            ok(
                "The session's account.",
                SessionInfoResponse(
                    handle = DocExamples.HANDLE,
                    did = did,
                    email = DocExamples.EMAIL,
                    emailConfirmed = true,
                    active = true,
                ),
            )
            bearerUnauthorized(ErrorEnvelope.PDS)
            notConfigured()
        }
    }

    val refreshSession: RouteConfig.() -> Unit = {
        bearerOperation(
            "refreshAtprotoSession",
            ApiTags.XRPC,
            "Refresh the session",
            """
            `com.atproto.server.refreshSession`. Send the `refreshJwt` (not the access token) as
            `Authorization: Bearer` and get a new `accessJwt` and `refreshJwt`. Replace both; the old refresh
            token is invalidated.
            """,
        )
        response {
            ok("A new session. Replace both stored tokens.", session)
            bearerUnauthorized(ErrorEnvelope.PDS)
            notConfigured()
        }
    }

    val deleteSession: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteAtprotoSession",
            ApiTags.XRPC,
            "End the session",
            """
            `com.atproto.server.deleteSession`. Send the `refreshJwt` as `Authorization: Bearer` to revoke it.
            The access token expires on its own.
            """,
        )
        response {
            ok("The session is ended. The body is an empty object.", EmptyPdsResponse())
            bearerUnauthorized(ErrorEnvelope.PDS)
            notConfigured()
        }
    }
}
