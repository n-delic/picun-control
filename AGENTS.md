# Picun Control

Open-source, offline, native Kotlin/Compose replacement for the "Picun Audio" earbud app.

**Start with `docs/HANDOFF.md`** (state, file locations, next steps), then `docs/PLAN.md`.
Reference: `docs/PROTOCOL.md` (BLE protocol), `docs/MODELS.md` (model DB), `docs/FINDINGS.md` (official app audit).

Rules:
- Facts not confirmed on real hardware stay marked UNVERIFIED in the docs; update docs when hardware confirms.
- Never send destructive commands (factory reset, rename, unknown) without a UI confirmation.
- App has no network permission, no accounts, no location, no analytics.
- `Protocol.kt` stays pure Kotlin; every packet builder/parser gets a unit test with known bytes.
- Build: `./gradlew testDebugUnitTest assembleDebug`.
