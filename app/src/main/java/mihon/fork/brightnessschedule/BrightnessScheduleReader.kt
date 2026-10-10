package mihon.fork.brightnessschedule

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/** Runs the schedule while the activity is visible; the first tick after each start is the catch-up. */
fun ComponentActivity.attachBrightnessSchedule() {
    val schedule = brightnessSchedule
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            schedule.runWhileVisible()
        }
    }
}
