# `:client:device`

This is a module that exposes interfaces for identifying the hardware device that the client is
running on.

The module also owns canonical journal identity and existing encryption-key custody.
Read [Identity custody](docs/identity-custody.md) before changing backup, restore, or
cross-platform transfer callers. It defines the read-only APIs, atomic storage contract,
and transport checks that remain the caller’s responsibility.
