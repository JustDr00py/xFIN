package com.xfin.core.designsystem.component

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    internal var focused = false
    internal val field = FocusRequester()
    internal var trailing: FocusRequester? = null

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
    return focusRequester(state.field)
        .onFocusChanged {
            state.focused = it.isFocused
            if (!it.isFocused) state.editing = false
        }
        .onPreviewKeyEvent { event ->
            // Keys for a button inside the field (its trailing icon) are that button's own.
            if (!state.focused) return@onPreviewKeyEvent false
            val code = event.key.nativeKeyCode
            val trailing = state.trailing
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && !state.editing && trailing != null) {
                if (event.type == KeyEventType.KeyDown) trailing.requestFocus()
                return@onPreviewKeyEvent true
            }
            val ok = code in OK_KEYS
            if (ok && event.type == KeyEventType.KeyUp) {
                if (state.editing) keyboard?.show() else state.editing = true
            }
            ok
        }
}

/**
 * A password field's show/hide button, for its `trailingIcon`. On TV the remote can't reach a
 * button inside a field by itself, so Right from the field moves onto it and Left goes back.
 */
@Composable
fun PasswordVisibilityButton(shown: Boolean, onToggle: () -> Unit, field: TvTextFieldState) {
    val focus = remember { FocusRequester() }
    DisposableEffect(field) {
        field.trailing = focus
        onDispose { field.trailing = null }
    }
    HeaderIconButton(
        onClick = onToggle,
        modifier = Modifier.focusRequester(focus).onPreviewKeyEvent { event ->
            val left = event.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_LEFT
            if (left && event.type == KeyEventType.KeyDown) field.field.requestFocus()
            left
        },
    ) {
        Icon(
            if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = if (shown) "Hide password" else "Show password",
        )
    }
}

/**
 * For a form's scrolling column. On TV, Compose keeps the focused item at a fixed point of a
 * scrolling container; a form scrolled to its end can't get there, keeps nudging the scroll by a
 * pixel, and shakes. Inside this, focus scrolls only as far as needed, as on phones.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MinimalScrollToFocus(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoView, content = content)
}

/** The interface defaults: scroll the least needed to show the focused item. */
private val MinimalBringIntoView = object : BringIntoViewSpec {}

private val OK_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
