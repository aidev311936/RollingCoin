package com.example.coinrain

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that CoinRainView releases all resources correctly across repeated
 * short-lived use cycles — the lifecycle pattern the rain() API creates.
 *
 * Manual stress-test procedure (50-cycle version, run after any lifecycle change):
 *   1. Build and install the debug APK.
 *   2. In Android Studio Profiler → Memory, record allocations.
 *   3. In the testapp: restart the Activity 50 times in quick succession
 *      (adb shell am force-stop + am start) while monitoring:
 *      - Thread count (must not grow)
 *      - Heap (must not grow monotonically)
 *      - Logcat for "SoundPool: still active" warnings
 *   4. Check that no "sensor listener still registered" warnings appear in Logcat.
 *
 * The automated tests below cover the programmatic API surface (no device rendering
 * required). They run on any connected device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class CoinRainViewLifecycleTest {

    private val ctx: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test
    fun stopWithoutStartDoesNotThrow() {
        onMain {
            val view = CoinRainView(ctx)
            view.stop()
        }
    }

    @Test
    fun stopIsIdempotent() {
        onMain {
            val view = CoinRainView(ctx)
            view.stop()
            view.stop()  // must not crash or double-release SoundPool
            view.stop()
        }
    }

    @Test
    fun stopNullsCallbackReferences() {
        onMain {
            val view = CoinRainView(ctx)
            view.onError = { _ -> }
            view.onAnimationFinished = {}
            view.stop()
            assertNull("onError must be null after stop()", view.onError)
            assertNull("onAnimationFinished must be null after stop()", view.onAnimationFinished)
        }
    }

    @Test
    fun tenRapidCreateDestroycycles() {
        // Simulates repeated short-lived use by the host (rain() pattern).
        // SoundPool and bitmap caches must not accumulate across cycles.
        repeat(10) {
            onMain {
                val view = CoinRainView(ctx)
                view.stop()
            }
        }
    }
}
