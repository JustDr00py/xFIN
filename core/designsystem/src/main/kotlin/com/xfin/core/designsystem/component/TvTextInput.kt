package com.xfin.core.designsystem.component

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * TV forms: the remote steps from box to box without the keyboard popping up on each one, and OK
 * opens it for the box you're on, as in the system's own forms. A focused text field starts typing
 * (and the TV shows its keyboard) by itself, so on TV a field stays read-only until OK.
 *
 * Use with a text field: `readOnly = state.readOnly` and `modifier = Modifier.tvTextField(state)`.
 * Phones are unaffected.
 */
@Stable
class TvTextFieldState internal constructor(private val isTv: Boolean) {
    internal var editing by mutableStateOf(false)

    /** Pass as the text field's `readOnly`. */
    val readOnly: Boolean get() = isTv && !editing
}

@Composable
fun rememberTvTextField(): TvTextFieldState {
    val isTv = LocalIsTv.current
    return remember(isTv) { TvTextFieldState(isTv) }
}

@Composable
fun Modifier.tvTextField(state: TvTextFieldState): Modifier {
    if (!LocalIsTv.current) return this
    val keyboard = LocalSoftwareKeyboardController.current
    // The field only accepts typing once it has recomposed as editable; then bring the keyboard up.
    LaunchedEffect(state.editing) {
        if (state.editing) {
            withFrameNanos { }
            keyboard?.show()
        }
    }
    return onFocusChanged { if (!it.isFocused) state.editing = false }
        .onPreviewKeyEvent { event ->
            val ok = event.key.nativeKeyCode in OK_KEYS
            if (ok && event.type == KeyEventType.KeyUp) {
                if (state.editing) keyboard?.show() else state.editing = true
            }
            ok
        }
}

private val OK_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
