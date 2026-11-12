package com.vaydelux.bousyplayer

import android.content.ComponentCallbacks2
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Activity recreation/trim lifecycle regression executed on the API 35
 * emulator in PR CI; real TV hardware still needs separate qualification.
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
