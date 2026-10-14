package com.payceylon.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.payceylon.app.ui.PayCeylonApp
import com.payceylon.app.ui.theme.PayCeylonTheme
import com.payceylon.app.ui.wallet.PaymentRequest
import com.payceylon.app.ui.wallet.parsePaymentRequest

// FragmentActivity (a ComponentActivity) because the system biometric prompt needs one.
class MainActivity : FragmentActivity() {
    private var pendingPaymentLink by mutableStateOf<PaymentRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh launch; after recreation the link has already been handled.
        if (savedInstanceState == null) pendingPaymentLink = intent.toPaymentRequest()
        setContent {
            PayCeylonTheme {
                PayCeylonApp(
                    pendingPaymentLink = pendingPaymentLink,
                    onPaymentLinkHandled = { pendingPaymentLink = null },
                )
            }
        }
    }

    /** singleTask: links opened while the app is running arrive here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toPaymentRequest()?.let { pendingPaymentLink = it }
    }

    private fun Intent?.toPaymentRequest(): PaymentRequest? =
        this?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString?.let(::parsePaymentRequest)
}
