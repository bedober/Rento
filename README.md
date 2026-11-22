# Rento — Rent Made Simple

Native Android tenancy and property management application for landlords, tenants and caretakers.

## Status
This repository was empty at initialization. The first commit establishes the project scaffold and secure integration boundaries. It is **not yet a production release**. Firebase project credentials, Pesapal merchant credentials, Africa's Talking credentials, Google Maps key, device testing and payment certification must be supplied and verified before live use.

## Stack
- Kotlin, Jetpack Compose, Navigation Compose, Hilt, MVVM, Clean Architecture
- Room for offline cache; Firebase Auth, Firestore, Cloud Functions, FCM and Storage
- Retrofit/OkHttp for backend APIs
- Pesapal API v3 for external collections and payouts
- Firebase Phone Auth (primary OTP); Africa's Talking from trusted backend only for optional custom SMS
- iText for generated PDFs (review commercial licensing before distribution)
- Google Maps SDK

## Modules
- `app/`: Android application
- `functions/`: Firebase Cloud Functions (Node.js/TypeScript) and Pesapal integration
- `docs/`: setup, security and release notes

## Setup
1. Install Android Studio with a current stable Android SDK and JDK 17.
2. Create a Firebase project and register Android package `com.bedober.rento`.
3. Enable Phone, Email/Password and Google providers in Firebase Authentication.
4. Create Firestore and Storage; deploy the rules in `firestore.rules` and `storage.rules`.
5. Download the matching `google-services.json` into `app/` (never commit production secrets).
6. Configure Maps SDK key using a local Gradle property or CI secret; restrict it to this package and signing certificate.
7. Configure Cloud Functions secrets using Firebase Secret Manager: `PESAPAL_CONSUMER_KEY`, `PESAPAL_CONSUMER_SECRET`, `PESAPAL_BASE_URL`, `PESAPAL_IPN_ID`, and optional `AT_API_KEY`, `AT_USERNAME`.
8. Start with Pesapal sandbox credentials and sandbox base URL. Register the IPN URL once and store the returned IPN ID. Use a public HTTPS function endpoint.
9. Run `./gradlew assembleDebug`; install `app/build/outputs/apk/debug/app-debug.apk`.

## Payments — mandatory invariants
- No Pesapal secret, Africa's Talking secret or wallet signing secret belongs in the APK.
- The client requests an order or withdrawal; only trusted Cloud Functions call Pesapal.
- Never credit a wallet from a client callback, redirect URL, screenshot or client-provided status.
- Verify each IPN with Pesapal Get Transaction Status before changing the ledger.
- Process callbacks idempotently using unique Pesapal tracking IDs / merchant references and Firestore transactions.
- Wallet and rent ledger mutations are server-side only. A successful rent transfer atomically debits tenant, credits landlord, applies any configured caretaker share and records a receipt reference.
- Payout capability depends on the merchant's actual Pesapal product/market approval. Do not assume a generic payment API account has disbursement access. Keep withdrawals disabled until the relevant payout API is confirmed for the merchant.
- Sandbox success is not evidence of production readiness. Reconcile fees, limits, reversals, disputes and settlement timing with Pesapal.

## Demo data
The app contains deterministic local demo fixtures for two properties, five units and three tenants. They are clearly marked DEMO and do not represent real payments or verified identities.

## Build and test
```bash
./gradlew test
./gradlew assembleDebug
./gradlew assembleRelease
```
Release signing must be configured through environment variables or an untracked local properties file. Never commit keystores or passwords.

## Important
This scaffold is a starting implementation, not a certified tenancy, financial, legal or tax product. Validate rental agreements against local law, privacy obligations, KYC/AML requirements, Pesapal merchant terms and applicable payment regulations before launch.
