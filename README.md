# Rento — Rent Made Simple

Native Android tenancy-management project for landlords, tenants and caretakers.

## Current implementation status
This repository is an **active development build**, not a finished or production-certified application.

Implemented source foundations:
- Kotlin + Jetpack Compose Android app shell and role-preview dashboard
- Firebase Auth repository methods for email/password, Google ID token and Firebase phone credential sign-in
- Firebase Phone Auth OTP request/resend controller
- Firebase Android configuration for project `rento-rental-app` and application ID `com.siege.rento`
- Firebase SDK dependencies for Auth, Firestore, Functions, FCM, Storage, Realtime Database and Crashlytics
- Android Firebase messaging receiver
- Firestore, Storage and Realtime Database security-rule starting points
- Room entities and offline data foundations
- Firebase Cloud Functions Pesapal order creation and IPN transaction-status verification skeleton

Not yet complete or verified:
- Full Compose authentication, role-selection and KYC screens
- End-to-end Google sign-in UI and OAuth client setup
- Full landlord/tenant/caretaker CRUD and realtime repository synchronization
- FCM token registration and targeted server-side notifications
- Crashlytics runtime validation and release mapping upload
- Deployment and emulator/device validation of Firebase rules
- Pesapal sandbox checkout and payout approval
- Android 12+ device test, release signing and production deployment

## Android identity and Firebase
- Android application ID: `com.siege.rento`
- Kotlin namespace: `com.bedober.rento` (source package namespace; intentionally distinct from the Play/Firebase application ID)
- Firebase project ID: `rento-rental-app`
- Firebase project number: `1083161589361`
- Firebase Storage bucket: `rento-rental-app.firebasestorage.app`

The Firebase configuration file is at `app/google-services.json`. It contains a client API key, which is an application identifier, not a server credential. Restrict that key in Google Cloud to the required Android app and APIs. Never commit service-account keys, OAuth client secrets, payment credentials or signing keystores.

## Firebase Console steps still required
1. Confirm Android app registration uses exactly `com.siege.rento`.
2. In Project settings → Your apps → Android app, add SHA-1 and SHA-256 fingerprints for both debug and release signing certificates.
3. Enable Authentication providers: Email/Password, Phone and Google.
4. Configure the Google provider and its support email. Add the generated Web OAuth client ID to the Android Google Sign-In flow; do not invent or hardcode a client secret.
5. Re-download `google-services.json` after OAuth configuration if Firebase generates updated OAuth client entries, then replace `app/google-services.json`.
6. Create the Cloud Firestore database in the intended region and deploy `firestore.rules`.
7. Create the default Storage bucket and deploy `storage.rules`.
8. Create a Realtime Database instance, set its region, then deploy `database.rules.json`. The rules deny all access except authenticated presence records at `/presence/{uid}`.
9. Enable Firebase Cloud Messaging. Android 13+ requires runtime notification permission; the app must request it before notifications can be displayed.
10. Enable Crashlytics in Firebase Console and validate a test crash in a non-production build.

## Deploy Firebase rules
Install and authenticate Firebase CLI with an account that has access to this Firebase project, then from the repository root run:

```bash
firebase login
firebase use rento-rental-app
firebase deploy --only firestore:rules,storage,database
```

The Realtime Database instance must exist before database rules can be deployed. Review rules in the Firebase Emulator Suite before production use.

## Build
Open in Android Studio with JDK 17 and Android SDK 35. CI uses Gradle 8.11.1 and builds a debug APK:
```bash
gradle assembleDebug
```
A successful CI build does not prove live Firebase services or Google OAuth are configured.

## Pesapal sandbox
Configure Firebase Secret Manager values:
- `PESAPAL_CONSUMER_KEY`
- `PESAPAL_CONSUMER_SECRET`
- `PESAPAL_BASE_URL` (Pesapal sandbox URL for sandbox testing)
- `PESAPAL_IPN_ID` (registered IPN notification ID)
- `RENTO_CHECKOUT_CALLBACK_URL` (real HTTPS callback/return page)

Deploy Cloud Functions and register `pesapalIPNListener` with Pesapal. The callable creates a pending order; the IPN handler verifies transaction status with Pesapal before wallet credit. Do not treat a WebView redirect as proof of payment.

### Payout restriction
`initiateWithdrawal` deliberately returns a failed-precondition response. Payout/disbursement access is a separate merchant capability and must be confirmed and approved by Pesapal. No payout access is assumed.

## Ledger safety
- The client cannot write wallet balances or payment transaction records.
- Rent settlement is performed in a Firestore transaction by `payRentFromWallet`.
- The callable verifies invoice ownership, invoice status and sufficient balance, then debits tenant, credits landlord and marks invoice paid atomically.
- Amounts are integer UGX values.
- The current ledger callable still needs server tests, authorization controls and reconciliation before real-money use.

## Android compatibility and release signing
Minimum SDK 26 (Android 8.0); target SDK 35. Android 12+ testing has not yet been performed. Configure a private release keystore and passwords in secure local properties or CI secrets. Never commit the keystore or passwords.
