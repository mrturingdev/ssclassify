# Privacy disclosures

Answers for the store privacy forms and the Sentry settings they depend on.
They match `privacy-policy.html`, the iOS `PrivacyInfo.xcprivacy` and what the
apps were verified to send (crash, session and log payloads inspected with
Sentry debug logging on 2026-10-06).

## Sentry project settings (do these before release)

| Setting | Value | Where |
|---|---|---|
| Data region | EU (already: DSN host is `ingest.de.sentry.io`) | Organization |
| Prevent Storing of IP Addresses | On | Project > Settings > Security & Privacy |
| Data Scrubber + Use Default Scrubbers | On | Project > Settings > Security & Privacy |
| Data retention | 30 days (the policy says 30; change both together if the plan differs) | Organization > Subscription |
| Spike protection | On | Organization > Spike Protection |
| Error sample rate | 100% (SDK default; nothing to change) | n/a |

## Google Play: Data safety form

**Data collection and security**

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | Yes | Crash reports, quality stats, and ML Kit metrics |
| Is all of the user data collected by your app encrypted in transit? | Yes | Sentry and Google endpoints are HTTPS only |
| Do you provide a way for users to request that their data is deleted? | No | No account or contact data, so reports can't be looked up; deleted automatically after 30 days. State this in the policy (done) |

**Data types** (none are shared: Sentry and Google process them as service providers)

| Play data type | Collected | Optional for the user | Purposes | Source |
|---|---|---|---|---|
| App info and performance > Crash logs | Yes | Yes (Settings toggle) | App functionality, Analytics | Sentry crash reports |
| App info and performance > Diagnostics | Yes | No | Analytics | ML Kit performance metrics (always), Sentry OCR-failure counts (opt-in) |
| App info and performance > Other app performance data | Yes | Yes | Analytics | Opt-in scan duration, AICore availability |
| App activity > App interactions | Yes | Yes | Analytics | Opt-in category corrections, Re-analyze all use |
| Device or other IDs | Yes | No | App functionality, Analytics | ML Kit per-installation ID (always), Sentry random install ID on crash reports |

Not collected: location, personal info, financial info, health, messages,
photos or videos (screenshots are read on-device only), audio, files and docs,
calendar, contacts, web browsing.

## App Store: App Privacy ("nutrition label")

iOS has no ML Kit (it uses Apple Vision), so only Sentry applies.

| App Store data type | Collected | Linked to the user | Used for tracking | Purposes |
|---|---|---|---|---|
| Diagnostics > Crash Data | Yes | No | No | App Functionality |
| Diagnostics > Performance Data | Yes | No | No | Analytics |
| Diagnostics > Other Diagnostic Data | Yes | No | No | Analytics |
| Usage Data > Product Interaction | Yes | No | No | Analytics |

## Open decision for you

**Is the crash-report install ID "linked to the user"?** It is a random value
created on install, regenerated whenever crash reports are switched off and on,
never tied to an account, name, email, ad ID or device ID, and never sent with
quality stats. The answers above treat it as not linked (Play: "Device or other
IDs", App Store: not declared as an identifier). If you want the stricter
reading, Play stays the same and the App Store gets
"Identifiers > Device ID: collected, linked, not tracking, App Functionality",
and Crash Data becomes linked.
