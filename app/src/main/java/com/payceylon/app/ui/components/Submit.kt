package com.payceylon.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.payceylon.app.data.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs one server action at a time for a screen: [busy] disables the button meanwhile, and
 * a failure shows the server's message.
 */
@Stable
class SubmitState internal constructor(private val scope: CoroutineScope, private val context: Context) {
    var busy by mutableStateOf(false)
        private set

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (e: ApiException) {
                showError(context, e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(context, "Something went wrong. Please try again.")
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberSubmitState(): SubmitState {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember(scope, context) { SubmitState(scope, context) }
}

fun showError(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/** The Activity hosting [this] context; phone verification needs one. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
