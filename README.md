# Rento — Rent Made Simple

Native Android tenancy and property management application for landlords, tenants and caretakers.

## Current repository status
The repository was empty when work began. The initial project foundation has now been committed: Android Gradle/Compose app shell, sample property dashboard, domain data types, Firebase security-rule starting points, Firebase Functions Pesapal top-up/IPN skeleton, architecture notes and CI build workflow.

**This is an early development scaffold, not a complete or production-ready tenancy/financial product.** Authentication screens, persistent Room/Firebase repositories, complete CRUD workflows, rent ledger, receipt/agreement generation, SMS/FCM workflows, reports, payout integration and release testing are still outstanding. No APK has yet been built or tested on a device.

## Stack target
- Kotlin, Jetpack Compose, Navigation Compose, Hilt, MVVM, Clean Architecture
- Room for offline cache; Firebase Auth, Firestore, Cloud Functions, FCM and Storage
- Retrofit/OkHttp for backend APIs
- Pesapal API v3 for external collections and approved payouts
- Firebase Phone Auth (primary OTP); Africa's Talking from trusted backend only for optional custom SMS
- iText for generated PDFs (review commercial licensing before distribution)
- Google Maps SDK

## Android demo
The current Compose screen provides a branded dashboard, role preview switch, wallet balance masking, bottom navigation and sample unit cards. Demo figures are static and are not backed by a real ledger.

## Setup
1. Install Android Studio with a current stable Android SDK and JDK 17.
2. Open the repository in Android Studio and allow Gradle sync.
3. Create a Firebase project and register Android package `com.bedober.rento`.
4. Enable Phone, Email/Password and Google providers in Firebase Authentication.
5. Create Firestore and Storage; deploy the rules in `firestore.rules` and `storage.rules`.
6. Download the matching `google-services.json` into `app/` (never commit production secrets). The current demo shell does not require it to compile; Firebase runtime features are not wired yet.
7. Configure Maps SDK key using a local Gradle property or CI secret; restrict it to this package and signing certificate.
8. Configure Cloud Functions secrets using Firebase Secret Manager: `PESAPAL_CONSUMER_KEY`, `PESAPAL_CONSUMER_SECRET`, `PESAPAL_BASE_URL`, `PESAPAL_IPN_ID`, and `RENTO_CHECKOUT_CALLBACK_URL`. Optional Africa's Talking secrets should be server-only.
9. Start with Pesapal sandbox credentials. Deploy the IPN HTTPS function, register its URL with Pesapal and store the returned IPN ID.
10. Build locally with Android Studio or `gradle assembleDebug` where Gradle 8.11.1 is installed. GitHub Actions is configured to assemble and upload a debug APK artifact on pushes and pull requests.

## Payments — mandatory invariants
- No Pesapal secret, Africa's Talking secret or wallet signing secret belongs in the APK.
- The client requests an order or withdrawal; only trusted Cloud Functions call Pesapal.
- Never credit a wallet from a client callback, redirect URL, screenshot or client-provided status.
- Verify each IPN with Pesapal Get Transaction Status before changing the ledger.
- Process callbacks idempotently using unique Pesapal tracking IDs / merchant references and Firestore transactions.
- Wallet and rent ledger mutations must be server-side only. A successful rent transfer must atomically debit tenant, credit landlord, apply any configured caretaker share and record a receipt reference.
- Payout capability depends on the merchant's actual Pesapal product/market approval. Do not assume a generic payment API account has disbursement access. Withdrawals remain disabled in this scaffold.
- Sandbox success is not evidence of production readiness. Reconcile fees, limits, reversals, disputes and settlement timing with Pesapal.

## Demo data
The UI includes five sample units across two sample property names and three occupied sample tenant labels. All are static demonstration data; no real identities, balances or payments are represented.

## Security and release
- Never commit `google-services.json` for a production project, service-account keys, API secrets, keystores or signing passwords.
- Release signing must use protected CI secrets or an untracked local properties file.
- Review KYC/privacy controls, rental agreement terms, local law, payment regulations and iText licensing before launch.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/PESAPAL_SANDBOX.md](docs/PESAPAL_SANDBOX.md).

## License
No license has been selected for this repository. Add an explicit license before external distribution.
