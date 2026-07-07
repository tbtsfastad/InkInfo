package com.example.inkinfo

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkInfoInstallAttributionInstrumentedTest {
    @Test
    fun readInstallAttributionOnDevice() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val params = InkInfoUtil.install_attribution(context)
            val cachedReferrerUrl = InkInfoUtil.referrer_url(context)

            val output = """
                build=${params.build}
                referrer_url=${params.referrer_url}
                cached_referrer_url=$cachedReferrerUrl
                install_version=${params.install_version}
                user_agent=${params.user_agent}
                lat=${params.lat}
                referrer_click_timestamp_seconds=${params.referrer_click_timestamp_seconds}
                install_begin_timestamp_seconds=${params.install_begin_timestamp_seconds}
                referrer_click_timestamp_server_seconds=${params.referrer_click_timestamp_server_seconds}
                install_begin_timestamp_server_seconds=${params.install_begin_timestamp_server_seconds}
                install_first_seconds=${params.install_first_seconds}
                last_update_seconds=${params.last_update_seconds}
                google_play_instant=${params.google_play_instant}
            """.trimIndent()

            println(output)
            Log.i("InkInfoAttribution", output)
        }
    }
}
