import { onCall, HttpsError, onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";

initializeApp();
const db = getFirestore();
const PESAPAL_KEY = defineSecret("PESAPAL_CONSUMER_KEY");
const PESAPAL_SECRET = defineSecret("PESAPAL_CONSUMER_SECRET");
const PESAPAL_BASE = defineSecret("PESAPAL_BASE_URL");
const PESAPAL_IPN = defineSecret("PESAPAL_IPN_ID");
const RENTO_CALLBACK = defineSecret("RENTO_CHECKOUT_CALLBACK_URL");

async function pesapalToken(): Promise<string> {
  const response = await fetch(`${PESAPAL_BASE.value()}/api/Auth/RequestToken`, {
    method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ consumer_key: PESAPAL_KEY.value(), consumer_secret: PESAPAL_SECRET.value() })
  });
  if (!response.ok) throw new Error(`Pesapal token request failed: ${response.status}`);
  const data = await response.json() as { token?: string };
  if (!data.token) throw new Error("Pesapal did not return an access token");
  return data.token;
}

/** Authenticated callable. Creates a pending order only; it never credits a wallet. */
export const createPesapalOrder = onCall({ secrets: [PESAPAL_KEY, PESAPAL_SECRET, PESAPAL_BASE, PESAPAL_IPN, RENTO_CALLBACK] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required");
  const amount = Number(request.data?.amount);
  if (!Number.isSafeInteger(amount) || amount < 1000 || amount > 50000000) throw new HttpsError("invalid-argument", "Amount must be UGX 1,000–50,000,000");
  const userSnap = await db.doc(`users/${request.auth.uid}`).get();
  if (!userSnap.exists) throw new HttpsError("failed-precondition", "Complete your profile first");
  const user = userSnap.data()!;
  if (!user.email || !user.phone || !user.name) throw new HttpsError("failed-precondition", "Name, email and phone are required");
  const merchantReference = `RNT-${request.auth.uid.slice(0,8)}-${Date.now()}`;
  const token = await pesapalToken();
  const orderResponse = await fetch(`${PESAPAL_BASE.value()}/api/Transactions/SubmitOrderRequest`, {
    method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({
      id: merchantReference, currency: "UGX", amount,
      description: "Rento wallet top-up",
      callback_url: RENTO_CALLBACK.value(),
      notification_id: PESAPAL_IPN.value(),
      billing_address: { email_address: user.email, phone_number: user.phone, first_name: user.name }
    })
  });
  if (!orderResponse.ok) throw new HttpsError("unavailable", "Pesapal order could not be created");
  const order = await orderResponse.json() as { order_tracking_id?: string; redirect_url?: string; error?: unknown };
  if (!order.order_tracking_id || !order.redirect_url) {
    logger.error("Invalid Pesapal order response", order.error);
    throw new HttpsError("internal", "Invalid Pesapal order response");
  }
  await db.collection("wallet_transactions").doc(order.order_tracking_id).create({
    userId: request.auth.uid, type: "TOPUP", amount, currency: "UGX",
    pesapalTrackingId: order.order_tracking_id, merchantReference,
    status: "PENDING", createdAt: FieldValue.serverTimestamp()
  });
  return { trackingId: order.order_tracking_id, redirectUrl: order.redirect_url, status: "PENDING" };
});

/** Public IPN endpoint. Verify status directly with Pesapal before a single atomic credit. */
export const pesapalIPNListener = onRequest({ secrets: [PESAPAL_KEY, PESAPAL_SECRET, PESAPAL_BASE] }, async (req, res) => {
  try {
    const trackingId = String(req.body?.OrderTrackingId ?? req.query.OrderTrackingId ?? "");
    if (!trackingId) { res.status(400).send("Missing tracking id"); return; }
    const token = await pesapalToken();
    const verify = await fetch(`${PESAPAL_BASE.value()}/api/Transactions/GetTransactionStatus?orderTrackingId=${encodeURIComponent(trackingId)}`, {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/json" }
    });
    if (!verify.ok) { res.status(502).send("Verification unavailable"); return; }
    const status = await verify.json() as { payment_status_description?: string; currency?: string; amount?: number };
    const txRef = db.collection("wallet_transactions").doc(trackingId);
    await db.runTransaction(async (t) => {
      const tx = await t.get(txRef);
      if (!tx.exists) { logger.warn("Unknown Pesapal tracking ID", trackingId); return; }
      const row = tx.data()!;
      if (row.status === "COMPLETED" || row.status === "FAILED") return;
      const paid = status.payment_status_description?.toUpperCase() === "COMPLETED" && Number(status.amount) === Number(row.amount) && status.currency === "UGX";
      if (!paid) {
        if (["FAILED", "INVALID"].includes(status.payment_status_description?.toUpperCase() ?? "")) t.update(txRef, { status: "FAILED", verifiedAt: FieldValue.serverTimestamp() });
        return;
      }
      const walletRef = db.doc(`wallets/${row.userId}`);
      t.set(walletRef, { balance: FieldValue.increment(row.amount), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      t.update(txRef, { status: "COMPLETED", verifiedAt: FieldValue.serverTimestamp(), providerStatus: status.payment_status_description });
    });
    res.status(200).send("OK");
  } catch (e) {
    logger.error("Pesapal IPN processing error", e);
    res.status(500).send("Retry");
  }
});

// Payouts remain off until Pesapal confirms payout product access and its API contract for this merchant.
export const initiateWithdrawal = onCall(async () => {
  throw new HttpsError("failed-precondition", "Withdrawals are disabled pending Pesapal payout approval and end-to-end reconciliation tests.");
});
