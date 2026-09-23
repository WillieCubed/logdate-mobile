package app.logdate.client.data.account

/** LogDate Cloud returned an account that does not match this installation's identity. */
internal class CanonicalOwnerMismatchException :
    IllegalStateException(
        "LogDate Cloud returned an account that does not match this installation's identity",
    )

/** This LogDate server does not support the required single-identity protocol. */
internal class UnsupportedCanonicalOwnerBindingException :
    IllegalStateException(
        "This LogDate server does not support the required single-identity protocol",
    )
