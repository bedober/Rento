# Pesapal sandbox checklist

1. Obtain sandbox merchant credentials from Pesapal and set the sandbox base URL in Firebase Secret Manager.
2. Set `PESAPAL_CONSUMER_KEY`, `PESAPAL_CONSUMER_SECRET`, `PESAPAL_BASE_URL`, `PESAPAL_IPN_ID`, and `RENTO_CHECKOUT_CALLBACK_URL` as Firebase secrets.
3. Deploy `pesapalIPNListener` and register its HTTPS URL through Pesapal's IPN registration API. Save the returned notification ID as `PESAPAL_IPN_ID`.
4. Confirm that the callback URL is a deployed HTTPS page/deep-link bridge owned by Rento. Never use a placeholder URL in a real order.
5. Deploy functions and test a top-up using the Pesapal sandbox checkout.
6. Verify the transaction by tracking ID from the server. Confirm amount and currency match the pending Firestore record before crediting.
7. Send duplicate IPNs and out-of-order status notifications. The ledger must credit exactly once.
8. Test failed, cancelled, pending and delayed transactions. A redirect to the app is not proof of payment.
9. Reconcile wallet credits with Pesapal sandbox reports before enabling any demo payout flow.

The current backend scaffold intentionally leaves withdrawals disabled. Confirm the merchant's access to Pesapal's payout/disbursement product and implement against its current approved API contract before exposing a withdrawal button. Payment checkout and payouts are separate capabilities.
