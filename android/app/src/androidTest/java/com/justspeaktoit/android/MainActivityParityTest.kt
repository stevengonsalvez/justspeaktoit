package com.justspeaktoit.android

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityParityTest {
    @Test
    fun mainActivityLaunchIntent_startsAndroidApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        instrumentation.waitForIdleSync()
        assertEquals("com.justspeaktoit.android.debug", context.packageName)
    }
}
