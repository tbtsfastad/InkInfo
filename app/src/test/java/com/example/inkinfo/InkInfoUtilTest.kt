package com.example.inkinfo

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InkInfoUtilTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication().applicationContext

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @After
    fun tearDown() {
        context.getSharedPreferences("ink_info", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun distinctId_generatesAndCachesValue() {
        val first = InkInfoUtil.distinct_id(context)
        val second = InkInfoUtil.distinct_id(context)

        UUID.fromString(first)
        assertEquals(first, second)
    }

    @Test
    fun distinctId_returnsCachedValue() {
        val cached = "cached-distinct-id"
        context.getSharedPreferences("ink_info", Context.MODE_PRIVATE)
            .edit()
            .putString("distinct_id", cached)
            .commit()

        assertEquals(cached, InkInfoUtil.distinct_id(context))
    }

    @Test
    fun logId_returnsNewUuidEachTime() {
        val first = InkInfoUtil.log_id()
        val second = InkInfoUtil.log_id()

        UUID.fromString(first)
        UUID.fromString(second)
        assertNotEquals(first, second)
    }

    @Test
    fun androidId_returnsSecureAndroidId() {
        val expected = "test-android-id"
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, expected)

        assertEquals(expected, InkInfoUtil.android_id(context))
    }

    @Test
    fun appVersion_returnsVersionName() {
        assertEquals("1.0", InkInfoUtil.app_version(context))
    }

    @Test
    fun zoneOffset_returnsCurrentTimezoneOffsetInHours() {
        val defaultTimezone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

            assertEquals(8, InkInfoUtil.zone_offset())
        } finally {
            TimeZone.setDefault(defaultTimezone)
        }
    }

    @Test
    fun manufacturerAndBrand_returnBuildValues() {
        val manufacturer = InkInfoUtil.manufacturer()
        val brand = InkInfoUtil.brand()

        assertEquals(Build.MANUFACTURER, manufacturer)
        assertEquals(Build.BRAND, brand)
        assertNotEquals("", manufacturer)
        assertNotEquals("", brand)
    }

    @Test
    fun deviceModelAndOsVersion_returnBuildValues() {
        assertEquals(Build.MODEL, InkInfoUtil.device_model())
        assertEquals(Build.VERSION.RELEASE, InkInfoUtil.os_version())
    }

    @Test
    fun osCountry_returnsLocaleCountry() {
        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)

            assertEquals("US", InkInfoUtil.os_country())
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun operator_returnsFixedMcc() {
        assertEquals("mcc", InkInfoUtil.`operator`())
    }

    @Test
    fun systemLanguage_returnsLanguageAndCountryWithUnderscore() {
        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)

            assertEquals("zh_CN", InkInfoUtil.system_language())
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun gaid_returnsEmptyStringWhenGooglePlayServicesIsUnavailable() = runBlocking {
        assertTrue(InkInfoUtil.gaid(context).isEmpty())
    }

    @Test
    fun installAttributionParams_holdsInstallAttributionValues() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val params = InkInfoUtil.InstallAttributionParams(
            build = "build/${Build.ID}",
            referrer_url = "utm_source=test",
            install_version = "1.0",
            user_agent = "test-user-agent",
            lat = true,
            referrer_click_timestamp_seconds = 11L,
            install_begin_timestamp_seconds = 22L,
            referrer_click_timestamp_server_seconds = 33L,
            install_begin_timestamp_server_seconds = 44L,
            install_first_seconds = packageInfo.firstInstallTime / 1000,
            last_update_seconds = packageInfo.lastUpdateTime / 1000,
            google_play_instant = true,
        )

        assertEquals("build/${Build.ID}", params.build)
        assertEquals("utm_source=test", params.referrer_url)
        assertEquals("1.0", params.install_version)
        assertTrue(params.user_agent.isNotEmpty())
        assertEquals(true, params.lat)
        assertEquals(11L, params.referrer_click_timestamp_seconds)
        assertEquals(22L, params.install_begin_timestamp_seconds)
        assertEquals(33L, params.referrer_click_timestamp_server_seconds)
        assertEquals(44L, params.install_begin_timestamp_server_seconds)
        assertEquals(packageInfo.firstInstallTime / 1000, params.install_first_seconds)
        assertEquals(packageInfo.lastUpdateTime / 1000, params.last_update_seconds)
        assertEquals(true, params.google_play_instant)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
