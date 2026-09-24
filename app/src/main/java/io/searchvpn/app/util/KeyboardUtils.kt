package io.searchvpn.app.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object KeyboardUtils {

    /**
     * Cleanly hides the soft keyboard (IME) and clears focus from the given view.
     * Uses WindowInsetsControllerCompat for modern insets animations and falls back
     * to InputMethodManager to ensure the IME hide animation completes without timing out.
     */
    fun hideKeyboard(view: View?) {
        if (view == null) return
        view.clearFocus()
        val context = view.context

        // Use modern WindowInsetsControllerCompat
        (context as? Activity)?.window?.let { window ->
            try {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.hide(WindowInsetsCompat.Type.ime())
            } catch (_: Exception) {
                // Ignore fallback to IMM
            }
        }

        // Reliable InputMethodManager dismissal
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (_: Exception) {
            // Safe ignore
        }
    }

    /**
     * Cleanly hides the soft keyboard for the currently focused view in the activity.
     */
    fun hideKeyboard(activity: Activity?) {
        if (activity == null) return
        val currentFocus = activity.currentFocus ?: activity.window.decorView
        hideKeyboard(currentFocus)
    }
}
