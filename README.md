# Rento — Rent Made Simple

Rento is an Android tenancy-management project using Kotlin, Jetpack Compose, Firebase and Firebase Cloud Functions. The repository currently contains a buildable Android foundation, Room cache, Firebase authentication primitives, FCM receiver, role-aware Firestore rules, admin configuration callable foundation, and Pesapal checkout/ledger functions.

## Current implementation status

- Android: Compose dashboard shell; Room entities/DAO/database; Firebase email, phone-credential and Google ID-token auth primitives; Hilt wiring; admin config repository; Firebase Functions wallet datasource.
- Backend: Node.js 20 callables for Pesapal v3 checkout, provider-side IPN verification and wallet credit, internal wallet rent settlement, config update and Pesapal connectivity test; monthly invoice and reminder schedules.
- Security: Firestore wallet balances and ledger are server-write-only. Provider secrets are not stored in Android or Firestore. Withdrawals fail closed until Pesapal enables a payout product for this merchant.
- Not yet production complete: full role-specific Compose flows, Facebook SDK login, Africa's Talking SMS delivery, PIN/biometric transaction authorization, KYC, payout integration, PDF/Excel generation, full media/chat/announcement workflows, emulator/security-rule test suite and end-to-end sandbox certification. Do not use demo accounts or test credentials for live tenants.

## Firebase setup

1. Create Firebase project rento-rental-app and register Android package com.siege.rento.
2. Download the real google-services.json from Firebase Project settings and replace the placeholder in the repository root.
3. Enable Email/Password, Phone and Google providers in Firebase Authentication. Configure SHA-1 and SHA-256 for the signing key used by the installed build.
4. Enable Firestore, Storage, Cloud Messaging and Cloud Functions. Deploy Firestore and Storage rules.
5. Set the production admin custom claim role=SUPER_ADMIN using a trusted Admin SDK environment. Never grant this role from an Android client.
6. Deploy functions from the functions directory using Firebase CLI.

## Pesapal sandbox

Use the Pesapal developer/merchant portal to create sandbox API credentials and register the public IPN URL. Configure Firebase Functions secrets: PESAPAL_CONSUMER_KEY, PESAPAL_CONSUMER_SECRET, PESAPAL_BASE_URL, PESAPAL_IPN_URL and PESAPAL_CALLBACK_URL. For sandbox use https://cybqa.pesapal.com/pesapalv3; for production use the official live API base https://pay.pesapal.com/v3. The IPN URL is the deployed pesapalIPNListener HTTPS endpoint; register it in Pesapal and save the returned IPN ID as PESAPAL_IPN_ID. The backend now fails closed if the API base or registered IPN ID is missing, rather than silently defaulting to sandbox or re-registering on each payment. IPN processing independently calls Pesapal transaction-status verification before crediting a wallet. A sandbox mobile-money number such as 256700000000 is only a test input where the Pesapal sandbox simulator accepts it; it is not proof of a completed payment.

Withdrawals: Pesapal checkout credentials do not automatically grant payout/disbursement access. The callable intentionally rejects withdrawals until the merchant has payout approval and the official payout API contract/credentials. No wallet is debited in this disabled state.

## Africa's Talking

Create an Africa's Talking sandbox or production application and keep its API key in Firebase Secret Manager. Do not place it in Android, Firestore-readable config, source control or chat. Scheduled reminders currently use FCM only; the Africa's Talking SMS adapter is not yet wired.

## Super Admin

Create the initial administrator through Firebase Authentication, then set the custom claim using a trusted Admin SDK script: admin.auth().setCustomUserClaims(uid, { role: 'SUPER_ADMIN' }); Also set users/{uid}.role to SUPER_ADMIN. Never expose Admin SDK credentials in the app. Firestore rules deny direct client writes to app configuration, wallets and transaction ledgers.

## Build

GitHub Actions installs Gradle 8.11.1 and runs gradle assembleDebug and gradle signingReport. The Android debug build has passed GitHub Actions. The workflow prints the CI debug SHA-1/SHA-256 fingerprints and uploads the APK artifact. This repository currently does not include the Gradle wrapper JAR, so ./gradlew assembleDebug is not yet available until a wrapper is generated and committed.

See functions/.env.example for server environment variable names. Real values belong in Firebase Functions Secret Manager and must never be committed.

## Release checklist

Before production: replace Firebase placeholder; deploy and test rules; configure OAuth SHA fingerprints; validate Pesapal IPN, currency, amount and duplicate notifications in sandbox; implement and test transaction PIN/biometric gating and KYC; obtain payout approval; complete Africa's Talking adapter; run emulator tests and a security review; create a release signing key and protect it outside Git.


## Production release gate

A successful debug CI build is not a production release. The signed Android App Bundle workflow is available at **Actions → Signed Android Release → Run workflow** after the repository owner adds these GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded private upload keystore
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Back up the keystore securely. Losing the Play App Signing upload key can delay future updates. Never commit the keystore or passwords.

Before enabling live payments, configure Firebase Functions secrets from the official Pesapal production merchant account: `PESAPAL_CONSUMER_KEY`, `PESAPAL_CONSUMER_SECRET`, `PESAPAL_BASE_URL=https://pay.pesapal.com/v3`, `PESAPAL_IPN_URL`, `PESAPAL_IPN_ID`, and `PESAPAL_CALLBACK_URL`. Confirm the production IPN is registered and test a real low-value transaction with the merchant. Do not use sandbox credentials in production. Keep withdrawal disabled until Pesapal approves the merchant for disbursements and supplies the payout API contract.

Also complete Firebase production configuration: enable required Auth providers, register release SHA-1/SHA-256 fingerprints, verify Firestore and Storage rules in the Firebase Emulator Suite, deploy Functions/rules, configure App Check and FCM, and run device testing on supported Android versions. The current UI is an authenticated foundation, not a completed end-to-end rental product; role-specific property, lease, invoice, maintenance, reporting and support workflows still require completion and acceptance testing.
