package com.example.inkump

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

@Suppress("unused")
object InkUmpUtil {

    private val CONSENT_COUNTRIES = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE",
        "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV",
        "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK",
        "SI", "ES", "SE", "NO", "IS", "LI", "CH", "GB"
    )

    private var isReqUmp = false

    var isUmpGranted: Boolean = false
        private set

    suspend fun request(
        activity: Activity? = null,
        onConsentFlowTrigger: (() -> Unit)? = null,
        onConsentUiShow: (() -> Unit)? = null,
        onFormRequest: (() -> Unit)? = null,
        onFormLoad: ((Boolean) -> Unit)? = null,
        onConsentStateUpdate: ((String) -> Unit)? = null
    ): Unit = withContext(Dispatchers.Main) {
        val device = Locale.getDefault().country
        if (!CONSENT_COUNTRIES.any { device.contains(it) }) {
            isUmpGranted = true
            isReqUmp = false
            return@withContext
        }
        activity ?: return@withContext
        if (isReqUmp) return@withContext
        isReqUmp = true
        onConsentFlowTrigger?.invoke()
        try {
            val app = activity.applicationContext
            val consentInformation = UserMessagingPlatform.getConsentInformation(app)
            val update = suspendCancellableCoroutine { continuation ->
                val parameters = ConsentRequestParameters.Builder()
                    .setTagForUnderAgeOfConsent(false)
                    .build()
                consentInformation.requestConsentInfoUpdate(
                    activity,
                    parameters,
                    { if (continuation.isActive) continuation.resume(true) },
                    { if (continuation.isActive) continuation.resume(false) }
                )
            }
            if (update && consentInformation.consentStatus != ConsentInformation.ConsentStatus.REQUIRED) {
                onConsentUiShow?.invoke()
                suspendCancellableCoroutine { continuation ->
                    onFormRequest?.invoke()
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                        onFormLoad?.invoke(it == null)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
            isUmpGranted = consentInformation.canRequestAds()
            onConsentStateUpdate?.invoke(queryPurpose(app))
        } finally {
            isReqUmp = false
        }
    }

    private fun queryPurpose(context: Context): String {
        return try {
            val sharedPref = context.getSharedPreferences("IABTCF", Context.MODE_PRIVATE)
            val purposeConsents = sharedPref.getString("IABTCF_PurposeConsents", "") ?: ""
            if (purposeConsents.isEmpty()) return "unknown"
            if (purposeConsents.all { it == '1' }) return "granted"
            if (purposeConsents.all { it == '0' }) return "denied"
            return "partial"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
