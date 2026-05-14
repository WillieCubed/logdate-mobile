-- Per-plan features for the Android Digital Credentials email-verification flow.
--
-- The `plans.features` JSONB column has existed since V16 but `StoredEntitlementService`
-- never read it, so paid plans never saw `email_verification: true` in their entitlement
-- response. Seeding the flag here makes the existing free / standard / pro plans visibly
-- consistent with the self-host `UnlimitedEntitlementService` (which already returned the
-- flag on as of commit 10485123).
--
-- The flag is a capability gate, not a paid differentiator: all three tiers can use
-- email verification once the Android client supports it. Production can still kill it
-- per-plan via a single UPDATE if Google's UserInfoCredential API regresses.

UPDATE plans
   SET features = COALESCE(features, '{}'::jsonb)
                  || jsonb_build_object('email_verification', true)
 WHERE id IN ('free', 'standard', 'pro');
