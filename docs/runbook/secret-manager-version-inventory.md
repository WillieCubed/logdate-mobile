# Secret Manager version inventory

Use the manual **Inventory Secret Manager Versions** workflow before pinning or
rotating a direct-Neon LogDate Cloud secret version. Select `staging` for
`logdate-dev` or `production` for `logdate`; the workflow uses that GitHub
Environment's configured OIDC identity.

It inventories only the seven required server secrets:
`logdate-db-url`, `logdate-db-user`, `logdate-db-password`,
`logdate-jwt-secret`, `logdate-server-encryption-key`,
`logdate-server-encryption-key-id`, and `logdate-health-internal-token`.
It prints only the environment/project label, the authenticated deploy
principal, these secret IDs, and their enabled numeric version IDs. It never
prints a secret value, URL, password, or the contents of a Secret Manager
version. It fails closed when any required secret has no enabled numeric
version.

The GitHub deploy identity receives Secret Manager Viewer at the project level
solely so this read-only check can distinguish a missing required container
from an empty version list. That role exposes metadata, not secret values. The
helper still queries and prints only the seven allowlisted IDs above; it never lists project secrets. The separate Secret Accessor grants remain restricted to
the three database secrets needed by migrations.

The workflow is inventory-only: it cannot run migrations, modify Cloud Run, or
alter traffic. It fails if the environment is not exactly `staging` or
`production`, if the active gcloud principal/project does not match the
selected environment, or if Google Cloud cannot list the requested metadata.
This check verifies the active principal and explicit target-project access; it does not prove environment isolation beyond that configured identity/project pairing.
