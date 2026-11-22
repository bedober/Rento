# Rento — Rent Made Simple

Native Android tenancy-management project for landlords, tenants and caretakers.

## Current implementation status
This repository is an **active development build**, not a finished or production-certified application.

Implemented source foundations:
- Kotlin + Jetpack Compose Android app shell and role-preview dashboard
- Domain models for user roles, properties, units and wallet transactions
- Firebase Auth repository methods for email/password, Google ID token and Firebase phone credential sign-in
- Firebase Phone Auth OTP request/resend controller (Activity integration still required)
- Room entities, DAO and database definition for offline property/unit/rent caches
- Firebase Cloud Functions Pesapal order creation and IPN transaction-status verification skeleton
- Server-side callable for atomic tenant-wallet to landlord-wallet rent invoice settlement
- Android Firebase messaging receiver
- Firestore/Storage security-rule starting points and Android CI workflow

Not yet complete or verified:
- Full Compose authentication, role-selection and KYC screens
- Complete landlord/tenant/caretaker property, lease, tenant and maintenance CRUD
- Room database provisioning, repositories, conflict handling and automatic Firebase sync
- Wallet PIN proof, biometric money-action gating and caretaker commission split
- Complete Pesapal IPN registration automation, real sandbox checkout test and delayed-status reconciliation
- Pesapal merchant-approved payout integration (currently disabled)
- In-app chat, announcement workflows, rent reminder scheduler and SMS delivery
- PDF receipts/agreements, Excel export, charts and analytics dashboards
- Android 12+ device test, accessibility test, release signing and production deployment

## Stack
Kotlin, Jetpack Compose, Navigation Compose, Hilt, MVVM, Clean Architecture, Room, Retrofit, Firebase Auth/Firestore/Functions/FCM/Storage, Pesapal API v3.

## Build
Open in Android Studio with JDK 17 and Android SDK 35. CI uses Gradle 8.11.1 and builds a debug APK. The repository does not yet include a verified release APK or production signing configuration.

## Firebase setup
1. Create a Firebase project and register package `com.bedober.rento`.
2. Enable Phone, Email/Password and Google providers.
3. Add SHA-1 and SHA-256 signing certificate fingerprints for phone auth and Google sign-in.
4. Add `google-services.json` to `app/` locally; it is ignored by Git.
5. Create Firestore and Storage, then deploy the rules.
6. Configure Android OAuth client IDs and provide the Google ID token to `FirebaseAuthRepository.signInWithGoogleIdToken`.
7. Integrate `PhoneOtpController` with an Activity/Compose OTP screen and call `signInWithPhoneCredential` after manual or automatic verification.

## Pesapal sandbox
Configure Firebase Secret Manager values:
- `PESAPAL_CONSUMER_KEY`
- `PESAPAL_CONSUMER_SECRET`
- `PESAPAL_BASE_URL` (Pesapal sandbox URL for sandbox testing)
- `PESAPAL_IPN_ID` (registered IPN notification ID)
- `RENTO_CHECKOUT_CALLBACK_URL` (real HTTPS callback/return page)

Deploy Cloud Functions and register `pesapalIPNListener` with Pesapal. The callable creates a pending order; the IPN handler verifies transaction status with Pesapal before wallet credit. Do not treat a WebView redirect as proof of payment.

### Payout restriction
`initiateWithdrawal` deliberately returns a failed-precondition response. Payout/disbursement access is a separate merchant capability and must be confirmed and approved by Pesapal for the specific account and market. Once approved, implement against the merchant's current payout API documentation, including destination validation, callback verification, idempotency, fee handling, reversals and settlement reconciliation. No payout access is assumed.

## Ledger safety
- The client cannot write wallet balances or payment transaction records.
- Rent settlement is performed in a Firestore transaction by `payRentFromWallet`.
- The callable verifies invoice ownership, invoice status and sufficient balance, then debits tenant, credits landlord and marks invoice paid atomically.
- Amounts are integer UGX values.
- The current ledger callable is an implementation foundation and still needs server tests, PIN authorization, multi-party commission accounting and reconciliation before real-money use.

## Android compatibility
Minimum SDK 26 (Android 8.0); target SDK 35. Android 12+ testing has not yet been performed. CI build success does not replace device verification.

## Release signing
Configure a private release keystore and passwords in a secure local properties file or CI secrets. Never commit the keystore, passwords, Firebase service-account key or payment secrets.
