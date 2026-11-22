# Rento architecture and implementation plan

## Trust boundaries
Android is an untrusted client. It may display cached data and collect user intent, but cannot authoritatively change wallet balances, rent settlement state, KYC state, commissions, payout state or audit records. Firestore security rules deny client writes to financial collections. Callable functions must verify Firebase Auth, role/ownership and validated input.

## Target package layout
```
app/src/main/java/com/bedober/rento/
  data/             Firebase and Room implementations, DTOs, mappers
  domain/
    model/          Property, RentalUnit, UserProfile, WalletTransaction
    repository/     Repository interfaces
    usecase/        Business operations
  presentation/
    auth/           Phone/email/Google sign-in and role selection
    landlord/       Portfolio, property/unit and tenant screens
    tenant/         Rent, lease and maintenance screens
    caretaker/      Assigned maintenance jobs
    wallet/         Balance, top-up, withdrawal, history and PIN
    common/         Navigation, theme, reusable Compose UI
  di/               Hilt modules
functions/src/
  payments/         Pesapal order, IPN verification and payout adapter
  ledger/           Atomic internal transfers, idempotency and audit log
  rent/             Rent generation, late fees, reminders
  notifications/    FCM and optional Africa's Talking
```

## Wallet ledger invariants
1. Store currency amounts as integer UGX minor units (UGX has no fractional subunit in this product).
2. Every money movement has an immutable ledger entry and a stable idempotency key.
3. A top-up stays PENDING until Pesapal's status endpoint confirms the exact tracking ID, amount and currency.
4. Rent payment is a server transaction that validates tenant assignment, rent due, PIN proof/session and sufficient available balance; it debits and credits all parties atomically.
5. A withdrawal reserves funds while PROCESSING; it is only finalized after the payout provider confirms settlement. A failure releases the reservation.
6. Reconciliation jobs compare internal records with provider transaction status and settlement reports.
7. No direct client writes to wallets, wallet_transactions, rent_payments, payout requests or audit logs.

## Planned completion gates
- [ ] Hilt application class, modules and ViewModels
- [ ] Room entities, DAOs, migrations and offline sync queue
- [ ] Firebase Auth phone/email/Google flows and account role claims
- [ ] Landlord property/unit CRUD, lease lifecycle and PDF agreement
- [ ] Tenant rent/bills, wallet and maintenance workflows
- [ ] Caretaker assignment and task lifecycle
- [ ] Server-side ledger and internal rent transfer
- [ ] Pesapal sandbox order/IPN tests and reconciliation
- [ ] Pesapal payout product approval and payout API integration
- [ ] FCM and Africa's Talking delivery receipts and opt-out handling
- [ ] KYC/privacy controls, PIN hashing, biometric-gated device authorization
- [ ] Automated tests, Android 12+ device matrix, accessibility and release signing
