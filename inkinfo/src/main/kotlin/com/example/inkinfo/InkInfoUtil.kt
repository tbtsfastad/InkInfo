package com.example.inkinfo

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.webkit.WebSettings
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
    private const val KEY_REFERRER_URL = "referrer_url"

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
        val preferences = context.getSharedPreferences("tba_info", Context.MODE_PRIVATE)
        val cachedDistinctId = preferences.getString("dtcdte", null)
        if (!cachedDistinctId.isNullOrEmpty()) {
            return cachedDistinctId
        }

        val distinctId = UUID.randomUUID().toString()
        preferences.edit().putString("dtcdte", distinctId).apply()
        return distinctId
    }

    fun log_id(): String = UUID.randomUUID().toString()

    fun referrer_url(context: Context): String {
        val appContext = context.applicationContext
        return appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REFERRER_URL, null)
            .orEmpty()
    }

    fun manufacturer(): String = Build.MANUFACTURER

    @Suppress("DEPRECATION")
    fun network_type(context: Context): String = runCatching {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetworkInfo
        if (network?.isConnected != true) {
            return@runCatching "no"
        }
        when (network.type) {
            ConnectivityManager.TYPE_WIFI -> "wifi"
            ConnectivityManager.TYPE_MOBILE -> "mobile"
            else -> "no"
        }
    }.getOrDefault("no")

    fun bundle_id(context: Context): String = context.packageName

    @SuppressLint("HardwareIds")
    fun android_id(context: Context): String = runCatching {
        val id = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        id?.takeUnless { it == "9774d56d682e549c" }.orEmpty()
    }.getOrDefault("")

    @Suppress("DEPRECATION")
    fun app_version(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
            .versionName.orEmpty()

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
        val language = locale.language.lowercase(Locale.ROOT)
        val country = locale.country.uppercase(Locale.ROOT)
        return "${language}_${country}"
    }

    suspend fun gaid(context: Context): String = withContext(Dispatchers.IO) {
        try {
            AdvertisingIdClient.getAdvertisingIdInfo(context.applicationContext).id.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun install_attribution(context: Context): InstallAttributionParams {
        val appContext = context.applicationContext
        val packageInfo = appContext.packageInfo()
        val referrerDetails = appContext.installReferrerDetails()
        val referrerUrl = referrerDetails?.installReferrer.orEmpty()
        if (referrerUrl.isNotEmpty()) {
            appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_REFERRER_URL, referrerUrl)
                .apply()
        }
        val adInfo = withContext(Dispatchers.IO) {
            runCatching {
                AdvertisingIdClient.getAdvertisingIdInfo(appContext)
            }.getOrNull()
        }

        return InstallAttributionParams(
            build = "build/${Build.ID}",
            referrer_url = referrerUrl,
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
        WebSettings.getDefaultUserAgent(context).orEmpty()
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
