package com.vaydelux.bousyplayer

import android.content.ComponentCallbacks2
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator-only lifecycle regression. CI compiles this suite but does
 * not claim to execute it until Android hardware/emulator qualification.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleTest {
    @Test
    fun recreateAndMemoryTrimWithoutActiveMediaNeverCrashes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
                activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
            }
            scenario.recreate()
        }
    }
}
