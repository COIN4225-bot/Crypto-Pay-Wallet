package com.example.auth

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed interface BiometricAvailability {
    data object Available : BiometricAvailability
    data class Unavailable(val reason: String) : BiometricAvailability
}

object ExternalSystemFlowTracker {
    @Volatile
    var isExternalFlowActive: Boolean = false

    fun beginExternalFlow() {
        isExternalFlowActive = true
    }

    fun endExternalFlow() {
        isExternalFlowActive = false
    }
}

object BiometricAuthManager {

    private const val TAG = "BiometricAuth"

    private fun supportedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
    }

    fun checkAvailability(context: Context): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(supportedAuthenticators())) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability.Unavailable("No biometric or device screen lock enrolled")
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricAvailability.Unavailable("No biometric hardware on this device")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.Unavailable("Biometric sensor temporarily unavailable")
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.Unavailable("Biometric security update required")
            else ->
                BiometricAvailability.Unavailable("Biometric authentication unsupported")
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "Authentication error ($errorCode): $errString")
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> onCancelled()
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT -> {
                        // Graceful fallback if device has no enrolled lock credentials
                        onSuccess()
                    }
                    else -> onError(errString.toString().ifBlank { "Authentication failed." })
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Biometric recognition failed; waiting for retry")
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock CryptoPay Wallet")
            .setSubtitle("Verify your identity to access your funds")
            .setConfirmationRequired(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(supportedAuthenticators())
        } else {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            builder.setNegativeButtonText("Cancel")
        }

        try {
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show BiometricPrompt", e)
            onError(e.localizedMessage ?: "Unable to launch biometric prompt.")
        }
    }
}
