package com.example.inkinfo

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.webkit.WebView
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.resume

object InkInfoUtil {
    private const val PREF_NAME = "ink_info"
    private const val KEY_DISTINCT_ID = "distinct_id"

    data class InstallAttributionParams(
        val build: String,
        val referrer_url: String,
        val install_version: String,
        val user_agent: String,
        val lat: Boolean,
        val referrer_click_timestamp_seconds: Long,
        val install_begin_timestamp_seconds: Long,
        val referrer_click_timestamp_server_seconds: Long,
        val install_begin_timestamp_server_seconds: Long,
        val install_first_seconds: Long,
        val last_update_seconds: Long,
        val google_play_instant: Boolean,
    )

    fun distinct_id(context: Context): String {
        val appContext = context.applicationContext
        val sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        return sp.getString(KEY_DISTINCT_ID, null)?.takeIf { it.isNotEmpty() } ?: synchronized(this) {
            sp.getString(KEY_DISTINCT_ID, null)?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
                .also { distinctId ->
                    sp.edit().putString(KEY_DISTINCT_ID, distinctId).commit()
                }
        }
    }

    fun log_id(): String = UUID.randomUUID().toString()

    fun manufacturer(): String = Build.MANUFACTURER

    @SuppressLint("HardwareIds")
    fun android_id(context: Context): String {
        val appContext = context.applicationContext
        return Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    }

    fun app_version(context: Context): String {
        val appContext = context.applicationContext
        return appContext.packageInfo().versionName.orEmpty()
    }

    fun zone_offset(): Int {
        return TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (60 * 60 * 1000)
    }

    fun brand(): String = Build.BRAND

    fun device_model(): String = Build.MODEL

    fun os_version(): String = Build.VERSION.RELEASE

    fun os_country(): String = Locale.getDefault().country

    fun `operator`(): String = "mcc"

    fun system_language(): String {
        val locale = Locale.getDefault()
        return if (locale.country.isEmpty()) {
            locale.language
        } else {
            "${locale.language}_${locale.country}"
        }
    }

    suspend fun gaid(context: Context): String = withContext(Dispatchers.IO) {
        runCatching {
            AdvertisingIdClient.getAdvertisingIdInfo(context.applicationContext).id.orEmpty()
        }.getOrDefault("")
    }

    suspend fun install_attribution(context: Context): InstallAttributionParams {
        val appContext = context.applicationContext
        val packageInfo = appContext.packageInfo()
        val referrerDetails = appContext.installReferrerDetails()
        val adInfo = withContext(Dispatchers.IO) {
            runCatching {
                AdvertisingIdClient.getAdvertisingIdInfo(appContext)
            }.getOrNull()
        }

        return InstallAttributionParams(
            build = "build/${Build.ID}",
            referrer_url = referrerDetails?.installReferrer.orEmpty(),
            install_version = referrerDetails?.installVersion.orEmpty(),
            user_agent = user_agent(appContext),
            lat = adInfo?.isLimitAdTrackingEnabled ?: false,
            referrer_click_timestamp_seconds = referrerDetails?.referrerClickTimestampSeconds ?: 0L,
            install_begin_timestamp_seconds = referrerDetails?.installBeginTimestampSeconds ?: 0L,
            referrer_click_timestamp_server_seconds = referrerDetails?.referrerClickTimestampServerSeconds ?: 0L,
            install_begin_timestamp_server_seconds = referrerDetails?.installBeginTimestampServerSeconds ?: 0L,
            install_first_seconds = packageInfo.firstInstallTime / 1000,
            last_update_seconds = packageInfo.lastUpdateTime / 1000,
            google_play_instant = referrerDetails?.googlePlayInstantParam ?: false,
        )
    }

    suspend fun user_agent(context: Context): String = withContext(Dispatchers.Main) {
        WebView(context.applicationContext).settings.userAgentString.orEmpty()
    }

    private fun Context.packageInfo(): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    private suspend fun Context.installReferrerDetails(): ReferrerDetails? = suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(this).build()

        continuation.invokeOnCancellation {
            runCatching { client.endConnection() }
        }

        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                val details = if (responseCode == InstallReferrerResponse.OK) {
                    runCatching { client.installReferrer }.getOrNull()
                } else {
                    null
                }
                runCatching { client.endConnection() }
                if (continuation.isActive) {
                    continuation.resume(details)
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                runCatching { client.endConnection() }
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        })
    }
}
