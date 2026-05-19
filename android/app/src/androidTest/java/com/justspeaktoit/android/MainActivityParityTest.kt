package com.justspeaktoit.android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityParityTest {
    @Test
    fun androidTestPackage_targetsDebugAndroidApp() {
        assertEquals("com.justspeaktoit.android.debug", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
    }
}
