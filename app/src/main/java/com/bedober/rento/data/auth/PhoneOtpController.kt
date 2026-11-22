package com.bedober.rento.data.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/**
 * Activity-scoped Firebase SMS verification controller.
 * Call start/resend from the OTP screen; never log verification codes or credentials.
 */
class PhoneOtpController(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun start(
        activity: Activity,
        e164Phone: String,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: (com.google.firebase.auth.PhoneAuthCredential) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        require(e164Phone.startsWith("+") && e164Phone.length in 9..16) { "Use an international phone number, e.g. +256..." }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                onAutoVerified(credential)
            }
            override fun onVerificationFailed(exception: FirebaseException) = onFailure(exception)
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                resendToken = token
                onCodeSent(id)
            }
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(e164Phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
        )
    }

    fun resend(
        activity: Activity,
        e164Phone: String,
        onCodeSent: (String) -> Unit,
        onAutoVerified: (com.google.firebase.auth.PhoneAuthCredential) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val token = resendToken ?: return onFailure(IllegalStateException("Resend token unavailable; request a new code"))
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) = onAutoVerified(credential)
            override fun onVerificationFailed(exception: FirebaseException) = onFailure(exception)
            override fun onCodeSent(id: String, newToken: PhoneAuthProvider.ForceResendingToken) {
                resendToken = newToken
                onCodeSent(id)
            }
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth).setPhoneNumber(e164Phone)
                .setTimeout(60L, TimeUnit.SECONDS).setActivity(activity)
                .setCallbacks(callbacks).setForceResendingToken(token).build()
        )
    }

    fun credential(verificationId: String, sixDigitCode: String) =
        PhoneAuthProvider.getCredential(verificationId, sixDigitCode)
}
