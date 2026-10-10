package mihon.fork.brightnesstoggle

import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.toggle
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/** Switches the reader's custom brightness filter; meant to be hit blind on a dimmed screen. */
@Composable
fun BrightnessToggleButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preference = remember { context.appGraph.readerPreferences.customBrightness }
    val enabled by preference.collectAsState()
    var lastToast by remember { mutableStateOf<Toast?>(null) }

    IconButton(
        onClick = {
            val nowEnabled = preference.toggle()
            lastToast?.cancel()
            lastToast = context.toast(if (nowEnabled) MR.strings.on else MR.strings.off)
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (enabled) DarkModeFilled else DarkModeOutlined,
            contentDescription = stringResource(MR.strings.pref_custom_brightness),
        )
    }
}
