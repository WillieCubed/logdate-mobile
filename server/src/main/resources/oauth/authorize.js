// Passkey sign-in on the browser authorization page. The server checks everything; this script
// only runs the WebAuthn ceremony and moves the browser on.
(() => {
    "use strict";

    const request = JSON.parse(document.getElementById("authorization-request").textContent);
    const button = document.getElementById("continue");
    const status = document.getElementById("status");

    class PageError extends Error {
        constructor(message, final) {
            super(message);
            this.final = final === true;
        }
    }

    const show = (message, kind) => {
        status.textContent = message;
        if (kind) {
            status.dataset.kind = kind;
        } else {
            delete status.dataset.kind;
        }
    };

    const fromBase64Url = (value) => {
        const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
        const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
        return Uint8Array.from(atob(padded), (character) => character.charCodeAt(0));
    };

    const toBase64Url = (buffer) => {
        let binary = "";
        new Uint8Array(buffer).forEach((byte) => {
            binary += String.fromCharCode(byte);
        });
        return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    };

    const expiredMessage = () =>
        new PageError(
            `This sign-in link has expired. Go back to ${request.client_name} and start signing in again.`,
            true,
        );

    const postJson = async (path, body) => {
        const response = await fetch(path, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "same-origin",
            body: JSON.stringify(body),
        });
        const payload = await response.json().catch(() => null);
        if (response.ok) return payload;
        if (payload && payload.error === "access_denied" && payload.error_description) {
            throw new PageError(payload.error_description);
        }
        if (payload && payload.error === "invalid_request") throw expiredMessage();
        throw new PageError("Passkey sign-in isn't available right now. Try again in a moment.");
    };

    const signIn = async () => {
        const options = await postJson("/oauth/authorize/passkey/options", { request_uri: request.request_uri });
        const credential = await navigator.credentials.get({
            publicKey: {
                challenge: fromBase64Url(options.challenge),
                rpId: options.rp_id,
                allowCredentials: options.allow_credentials.map((id) => ({ type: "public-key", id: fromBase64Url(id) })),
                userVerification: options.user_verification,
                timeout: options.timeout,
            },
        });
        if (!credential) throw new PageError("No passkey was chosen. Try again.");
        const assertion = credential.response;
        const approval = await postJson("/oauth/authorize/passkey", {
            request_uri: request.request_uri,
            challenge: options.challenge,
            credential: {
                id: credential.id,
                rawId: toBase64Url(credential.rawId),
                type: credential.type,
                response: {
                    clientDataJSON: toBase64Url(assertion.clientDataJSON),
                    authenticatorData: toBase64Url(assertion.authenticatorData),
                    signature: toBase64Url(assertion.signature),
                    userHandle: assertion.userHandle ? toBase64Url(assertion.userHandle) : "",
                },
            },
        });
        show(`Signed in. Returning to ${request.client_name}…`, "success");
        window.location.assign(approval.redirect_uri);
    };

    if (!window.PublicKeyCredential || !navigator.credentials) {
        button.disabled = true;
        show("This browser can't use passkeys. Open this page in a browser that supports them.", "error");
        return;
    }

    button.addEventListener("click", async () => {
        button.disabled = true;
        show("Waiting for your passkey…");
        try {
            await signIn();
        } catch (error) {
            if (error instanceof PageError) {
                show(error.message, "error");
                button.disabled = error.final;
                return;
            }
            button.disabled = false;
            if (error && error.name === "NotAllowedError") {
                show("Passkey sign-in was cancelled or timed out. Try again when you're ready.", "error");
                return;
            }
            show("Passkey sign-in didn't work. Try again.", "error");
        }
    });
})();
