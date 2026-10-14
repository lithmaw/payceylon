package com.payceylon.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.ui.theme.InterFontFamily
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import android.os.SystemClock
import androidx.compose.runtime.DisposableEffect
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.payceylon.app.ui.account.AccountSetting
import com.payceylon.app.ui.account.AddBankScreen
import com.payceylon.app.ui.account.AddCardScreen
import com.payceylon.app.ui.account.EditProfileFieldScreen
import com.payceylon.app.ui.account.LegalDocument
import com.payceylon.app.ui.account.LegalScreen
import com.payceylon.app.ui.account.PaymentMethodsScreen
import com.payceylon.app.ui.account.ProfileField
import com.payceylon.app.ui.account.ProfileScreen
import com.payceylon.app.ui.account.SettingsScreen
import com.payceylon.app.ui.auth.ConnectionProblemScreen
import com.payceylon.app.ui.auth.PayCeylonAuthFlow
import com.payceylon.app.ui.auth.ProfileSetupScreen
import com.payceylon.app.ui.components.AppLockScreen
import com.payceylon.app.ui.components.AppLockSettings
import com.payceylon.app.ui.components.PayCeylonEasing
import com.payceylon.app.ui.components.canLockApp
import com.payceylon.app.ui.components.findActivity
import com.payceylon.app.ui.components.promptForUnlock
import com.payceylon.app.ui.components.showError
import com.payceylon.app.ui.theme.CryptoWalletColors
import com.payceylon.app.ui.theme.FiatWalletColors
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.wallet.Asset
import com.payceylon.app.ui.wallet.HomeScreen
import com.payceylon.app.ui.wallet.PaymentRequest
import com.payceylon.app.ui.wallet.QrScanScreen
import com.payceylon.app.ui.wallet.ReceiveScreen
import com.payceylon.app.ui.wallet.SendMoneyScreen
import com.payceylon.app.ui.wallet.SendToUsernameScreen
import com.payceylon.app.ui.wallet.SessionState
import com.payceylon.app.ui.wallet.SwapScreen
import com.payceylon.app.ui.wallet.TopUpScreen
import com.payceylon.app.ui.wallet.TransactionDetailScreen
import com.payceylon.app.ui.wallet.TransactionsScreen
import com.payceylon.app.ui.wallet.WalletSwitchOverlay
import com.payceylon.app.ui.wallet.WalletType
import com.payceylon.app.ui.wallet.WalletViewModel
import com.payceylon.app.ui.wallet.WithdrawScreen
import com.payceylon.app.data.ApiException
import com.payceylon.app.ui.wallet.toAmountString
import com.payceylon.app.ui.wallet.toKeypadInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Shown while the wallet comes from the device's local database because the server is unreachable. */
@Composable
private fun OfflineNotice() {
    Text(
        text = "Offline · showing saved data",
        modifier = Modifier
            .background(Color(0xE6202022), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = Color.White,
        fontFamily = InterFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Ordered by depth so transitions can slide forward or back. */
private enum class AppDestination {
    Auth, ProfileSetup, Home, Transactions, Receive, Swap, TopUp, Withdraw,
    Settings, Profile, EditProfileField, PaymentMethods, AddCard, AddBank, Legal,
    TransactionDetail, QrScan, SendToUsername, SendMoney,
}

private val AccountDestinations = setOf(
    AppDestination.Settings,
    AppDestination.Profile,
    AppDestination.EditProfileField,
    AppDestination.PaymentMethods,
    AppDestination.AddCard,
    AppDestination.AddBank,
    AppDestination.Legal,
)

@Composable
fun PayCeylonApp(
    walletViewModel: WalletViewModel = viewModel(),
    /** A `payceylon://` link the app was opened with, waiting to be shown. */
    pendingPaymentLink: PaymentRequest? = null,
    onPaymentLinkHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val account = walletViewModel.account
    // With a saved sign-in, start on Home (hidden under the loading cover) so the wallet appears
    // without onboarding sliding past first.
    var destination by rememberSaveable {
        mutableStateOf(if (walletViewModel.session == SessionState.SignedOut) AppDestination.Auth else AppDestination.Home)
    }
    var recipientHandle by rememberSaveable { mutableStateOf("") }
    // Amount and wallet carried over from a scanned or linked payment request, if it had one.
    var prefillAmount by rememberSaveable { mutableStateOf("") }
    var prefillAsset by rememberSaveable { mutableStateOf<Asset?>(null) }
    // Where Send Money's back button returns to.
    var sendMoneyOrigin by rememberSaveable { mutableStateOf(AppDestination.QrScan) }
    // Where Add card / Add bank return to (Top Up, Withdraw or the payment methods list).
    var addMethodOrigin by rememberSaveable { mutableStateOf(AppDestination.PaymentMethods) }
    // Where Transaction details returns to: Home or the full list.
    var detailOrigin by rememberSaveable { mutableStateOf(AppDestination.Home) }
    var editingField by rememberSaveable { mutableStateOf(ProfileField.Name) }
    var legalDocument by rememberSaveable { mutableStateOf(LegalDocument.Privacy) }
    var walletSwitch by remember { mutableStateOf<WalletType?>(null) }
    var selectedTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    val session = walletViewModel.session
    // Bumped for every new payment request so Send Money starts fresh even if it is
    // already on screen (e.g. a link opened while the user was mid-payment).
    var sendMoneySession by rememberSaveable { mutableIntStateOf(0) }

    fun openSendMoney(request: PaymentRequest, origin: AppDestination) {
        recipientHandle = request.handle
        prefillAmount = request.amount?.toKeypadInput((request.asset ?: Asset.LKR).decimals).orEmpty()
        prefillAsset = request.asset
        sendMoneyOrigin = origin
        sendMoneySession++
        destination = AppDestination.SendMoney
    }

    fun openAddMethod(target: AppDestination, origin: AppDestination) {
        addMethodOrigin = origin
        destination = target
    }

    /** Where the system back gesture and on-screen back buttons lead from each screen. */
    fun parentOf(screen: AppDestination): AppDestination = when (screen) {
        AppDestination.Auth, AppDestination.ProfileSetup, AppDestination.Home -> screen
        AppDestination.Profile, AppDestination.PaymentMethods, AppDestination.Legal -> AppDestination.Settings
        AppDestination.EditProfileField -> AppDestination.Profile
        AppDestination.AddCard, AppDestination.AddBank -> addMethodOrigin
        AppDestination.SendToUsername -> AppDestination.QrScan
        AppDestination.SendMoney -> sendMoneyOrigin
        AppDestination.TransactionDetail -> detailOrigin
        else -> AppDestination.Home
    }

    fun goBack() {
        destination = parentOf(destination)
    }

    // The session decides whether the user sees sign-in, profile setup or the wallet.
    LaunchedEffect(session) {
        when (session) {
            SessionState.SignedOut -> destination = AppDestination.Auth
            SessionState.NeedsProfile -> destination = AppDestination.ProfileSetup
            SessionState.SignedIn -> if (destination == AppDestination.Auth || destination == AppDestination.ProfileSetup) {
                destination = AppDestination.Home
            }
            else -> Unit
        }
    }

    // Keep balances, history and notifications current while the app is on screen, so money
    // sent from another phone shows up within a few seconds.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(session) {
        if (session != SessionState.SignedIn) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                walletViewModel.refreshQuietly()
                delay(5_000)
            }
        }
    }

    LaunchedEffect(Unit) {
        walletViewModel.messages.collect { showError(context, it) }
    }

    // Biometric unlock: locks a signed-in app at launch and after 30 s in the background.
    val appLock = remember { AppLockSettings(context) }
    var appLockEnabled by remember { mutableStateOf(appLock.enabled) }
    var locked by rememberSaveable { mutableStateOf(appLock.enabled && walletViewModel.session != SessionState.SignedOut) }
    LaunchedEffect(session) { if (session == SessionState.SignedOut) locked = false }
    DisposableEffect(lifecycleOwner) {
        var backgroundedAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START ->
                    if (appLock.enabled && backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt > 30_000) {
                        locked = true
                    }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /**
     * With "Biometric approval" on, money only leaves after the user passes the system prompt.
     * Phones without biometrics or a screen lock skip it (nothing to confirm with).
     */
    suspend fun confirmPayment(description: String) {
        if (!account.isEnabled(AccountSetting.BiometricApproval) || !canLockApp(context)) return
        val activity = context.findActivity() as? FragmentActivity ?: return
        val passed = suspendCancellableCoroutine { continuation ->
            promptForUnlock(activity, description) { if (continuation.isActive) continuation.resume(it) }
        }
        if (!passed) throw ApiException(0, "NOT_CONFIRMED", "Payment not confirmed.")
    }

    fun setAppLock(enable: Boolean) {
        val activity = context.findActivity() as? FragmentActivity
        when {
            !enable -> {
                appLock.enabled = false
                appLockEnabled = false
            }
            !canLockApp(context) -> showError(context, "Set up a fingerprint or screen lock in your phone's settings first.")
            activity != null -> promptForUnlock(activity, "Turn on biometric unlock") { passed ->
                if (passed) {
                    appLock.enabled = true
                    appLockEnabled = true
                }
            }
        }
    }

    val inWallet = destination != AppDestination.Auth && destination != AppDestination.ProfileSetup

    // Deep links wait until the user has signed in, then open straight into Send Money
    // (or Receive, for the user's own code).
    LaunchedEffect(pendingPaymentLink, inWallet) {
        val link = pendingPaymentLink ?: return@LaunchedEffect
        if (!inWallet) return@LaunchedEffect
        if (link.handle.equals(account.profile.handle, ignoreCase = true)) {
            destination = AppDestination.Receive
        } else {
            openSendMoney(link, origin = AppDestination.Home)
        }
        onPaymentLinkHandled()
    }

    BackHandler(enabled = inWallet && destination != AppDestination.Home) {
        goBack()
    }

    // Onboarding and OTP always use the light palette; the wallet follows the selected wallet.
    val palette = when {
        !inWallet -> FiatWalletColors
        walletViewModel.selectedWallet == WalletType.Crypto -> CryptoWalletColors
        else -> FiatWalletColors
    }
    val view = LocalView.current
    LaunchedEffect(palette.isDark, destination) {
        // The scanner manages its own (always light) status bar icons.
        if (destination == AppDestination.QrScan || view.isInEditMode) return@LaunchedEffect
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }
    }

    CompositionLocalProvider(LocalWalletColors provides palette) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.page),
        ) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val slideSpec = spring<IntOffset>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = IntOffset(1, 1),
                    )
                    when {
                        // Settings and its sub-pages use a lighter, quicker slide than the wallet flows.
                        initialState in AccountDestinations || targetState in AccountDestinations -> {
                            val forward = targetState > initialState
                            val lightSlide = tween<IntOffset>(220, easing = PayCeylonEasing)
                            (slideInHorizontally(lightSlide) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(lightSlide) { if (forward) -it / 12 else it / 12 } + fadeOut(tween(120)))
                        }
                        targetState > initialState ->
                            (slideInHorizontally(slideSpec) { it } + fadeIn(tween(160))) togetherWith
                                (slideOutHorizontally(slideSpec) { -it / 3 } + fadeOut(tween(120)))
                        else ->
                            (slideInHorizontally(slideSpec) { -it / 3 } + fadeIn(tween(160))) togetherWith
                                (slideOutHorizontally(slideSpec) { it } + fadeOut(tween(120)))
                    }
                },
                label = "app navigation",
            ) { target ->
                when (target) {
                    AppDestination.Auth -> PayCeylonAuthFlow(
                        onAuthenticated = walletViewModel::restoreSession,
                    )

                    AppDestination.ProfileSetup -> ProfileSetupScreen(
                        isHandleAvailable = account::isHandleAvailable,
                        onSave = walletViewModel::completeProfile,
                    )

                    AppDestination.Home -> HomeScreen(
                        selectedWallet = walletViewModel.selectedWallet,
                        selectedToken = walletViewModel.selectedToken,
                        balanceOf = walletViewModel::balanceOf,
                        transactions = walletViewModel.transactions,
                        lastShownBalance = { walletViewModel.lastShownBalance },
                        onBalanceShown = { asset, value -> walletViewModel.lastShownBalance = asset to value },
                        holdBalanceCount = walletSwitch != null,
                        onWalletChange = { wallet ->
                            if (wallet != walletViewModel.selectedWallet && walletSwitch == null) {
                                walletSwitch = wallet
                            }
                        },
                        onTokenChange = { walletViewModel.selectedToken = it },
                        addressOf = walletViewModel::addressOf,
                        onSend = { destination = AppDestination.QrScan },
                        onReceive = { destination = AppDestination.Receive },
                        onSwap = { destination = AppDestination.Swap },
                        onTopUp = { destination = AppDestination.TopUp },
                        onWithdraw = { destination = AppDestination.Withdraw },
                        onTransactionClick = { transaction ->
                            selectedTransactionId = transaction.id
                            detailOrigin = AppDestination.Home
                            destination = AppDestination.TransactionDetail
                        },
                        onSeeAllTransactions = { destination = AppDestination.Transactions },
                        photo = account.photo,
                        userName = account.profile.name,
                        notifications = account.notifications,
                        onMarkAllNotificationsRead = account::markAllRead,
                        onNotificationClick = { notification ->
                            account.markRead(notification.id)
                            val transactionId = notification.transactionId
                            if (transactionId != null && walletViewModel.transactions.any { it.id == transactionId }) {
                                selectedTransactionId = transactionId
                                detailOrigin = AppDestination.Home
                                destination = AppDestination.TransactionDetail
                            } else {
                                destination = AppDestination.Settings
                            }
                        },
                        onProfileClick = { destination = AppDestination.Settings },
                        hideBalanceByDefault = account.isEnabled(AccountSetting.HideBalance),
                        monthlyChangeOf = walletViewModel::monthlyChangePercent,
                        cards = account.cards,
                        primaryCard = account.primaryCard,
                        onPrimaryCardChange = { account.selectPrimaryCard(it.id) },
                    )

                    AppDestination.Transactions -> TransactionsScreen(
                        wallet = walletViewModel.selectedWallet,
                        transactions = walletViewModel.transactions,
                        onBack = ::goBack,
                        onTransactionClick = { transaction ->
                            selectedTransactionId = transaction.id
                            detailOrigin = AppDestination.Transactions
                            destination = AppDestination.TransactionDetail
                        },
                    )

                    AppDestination.Receive -> ReceiveScreen(
                        user = account.profile.user,
                        photo = account.photo,
                        initialAsset = walletViewModel.selectedAsset,
                        onBack = ::goBack,
                    )

                    AppDestination.Swap -> SwapScreen(
                        initialFrom = walletViewModel.selectedAsset,
                        balanceOf = walletViewModel::balanceOf,
                        onSwap = walletViewModel::swap,
                        onBack = ::goBack,
                        onDone = { destination = AppDestination.Home },
                    )

                    AppDestination.TopUp -> TopUpScreen(
                        methods = account.paymentMethods,
                        onTopUp = { amount, method, key ->
                            confirmPayment("Top up LKR ${amount.toAmountString(Asset.LKR)} from ${method.title}")
                            walletViewModel.topUp(amount, method, key)
                        },
                        onAddMethod = { openAddMethod(AppDestination.AddCard, origin = AppDestination.TopUp) },
                        onBack = ::goBack,
                        onDone = { destination = AppDestination.Home },
                    )

                    AppDestination.Withdraw -> WithdrawScreen(
                        balance = walletViewModel.balanceOf(Asset.LKR),
                        banks = account.banks,
                        onWithdraw = { amount, bank, key ->
                            confirmPayment("Withdraw LKR ${amount.toAmountString(Asset.LKR)} to ${bank.title}")
                            walletViewModel.withdraw(amount, bank, key)
                        },
                        onAddBank = { openAddMethod(AppDestination.AddBank, origin = AppDestination.Withdraw) },
                        onBack = ::goBack,
                        onDone = { destination = AppDestination.Home },
                    )

                    AppDestination.Settings -> SettingsScreen(
                        account = account,
                        appVersion = remember {
                            runCatching {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            }.getOrNull() ?: "1.0"
                        },
                        onBack = ::goBack,
                        onEditProfile = { destination = AppDestination.Profile },
                        onPaymentMethods = { destination = AppDestination.PaymentMethods },
                        onLegal = { document ->
                            legalDocument = document
                            destination = AppDestination.Legal
                        },
                        onHelp = {
                            val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@payceylon.lk"))
                                .putExtra(Intent.EXTRA_SUBJECT, "PayCeylon support (@${account.profile.handle})")
                            try {
                                context.startActivity(email)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "Email support@payceylon.lk", Toast.LENGTH_LONG).show()
                            }
                        },
                        onLogOut = walletViewModel::logOut,
                        appLockEnabled = appLockEnabled,
                        onAppLockChange = ::setAppLock,
                    )

                    AppDestination.Profile -> ProfileScreen(
                        account = account,
                        onBack = ::goBack,
                        onEditField = { field ->
                            editingField = field
                            destination = AppDestination.EditProfileField
                        },
                    )

                    AppDestination.EditProfileField -> EditProfileFieldScreen(
                        account = account,
                        field = editingField,
                        onBack = ::goBack,
                        onSaved = { destination = AppDestination.Profile },
                    )

                    AppDestination.PaymentMethods -> PaymentMethodsScreen(
                        account = account,
                        onBack = ::goBack,
                        onAddCard = { openAddMethod(AppDestination.AddCard, origin = AppDestination.PaymentMethods) },
                        onAddBank = { openAddMethod(AppDestination.AddBank, origin = AppDestination.PaymentMethods) },
                    )

                    AppDestination.AddCard -> AddCardScreen(
                        account = account,
                        onBack = ::goBack,
                        onAdded = { goBack() },
                    )

                    AppDestination.AddBank -> AddBankScreen(
                        account = account,
                        onBack = ::goBack,
                        onAdded = { goBack() },
                    )

                    AppDestination.Legal -> LegalScreen(document = legalDocument, onBack = ::goBack)

                    AppDestination.TransactionDetail -> {
                        val transaction = walletViewModel.transactions.firstOrNull { it.id == selectedTransactionId }
                        if (transaction == null) {
                            LaunchedEffect(Unit) { destination = AppDestination.Home }
                        } else {
                            TransactionDetailScreen(
                                transaction = transaction,
                                onBack = ::goBack,
                            )
                        }
                    }

                    AppDestination.QrScan -> QrScanScreen(
                        ownHandle = account.profile.handle,
                        onBack = ::goBack,
                        onRecipientFound = { request -> openSendMoney(request, origin = AppDestination.QrScan) },
                        onSendToUsername = { destination = AppDestination.SendToUsername },
                    )

                    AppDestination.SendToUsername -> SendToUsernameScreen(
                        recentRecipients = walletViewModel.recentRecipients,
                        searchUsers = { query -> walletViewModel.searchUsers(query) },
                        onBack = ::goBack,
                        onRecipientSelected = { handle ->
                            openSendMoney(PaymentRequest(handle), origin = AppDestination.SendToUsername)
                        },
                    )

                    AppDestination.SendMoney -> key(sendMoneySession) {
                        SendMoneyScreen(
                            recipientHandle = recipientHandle,
                            initialAsset = prefillAsset ?: walletViewModel.selectedAsset,
                            initialAmount = prefillAmount,
                            balanceOf = walletViewModel::balanceOf,
                            onSend = { amount, asset, key ->
                                confirmPayment("Send ${asset.code} ${amount.toAmountString(asset)} to @$recipientHandle")
                                walletViewModel.send(recipientHandle, amount, asset, key)
                            },
                            onBack = ::goBack,
                            onDone = { destination = AppDestination.Home },
                        )
                    }
                }
            }

            when (session) {
                // Brief, while a saved sign-in is checked with the server.
                SessionState.Checking -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.page),
                )
                is SessionState.Unreachable -> ConnectionProblemScreen(
                    message = session.message,
                    onRetry = walletViewModel::restoreSession,
                    onSignOut = walletViewModel::logOut,
                )
                else -> Unit
            }

            AnimatedVisibility(
                visible = walletViewModel.isOffline && inWallet && session == SessionState.SignedIn,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 6.dp),
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
            ) {
                OfflineNotice()
            }

            if (locked && session != SessionState.SignedOut) {
                AppLockScreen(
                    onUnlocked = { locked = false },
                    onSignOut = {
                        locked = false
                        walletViewModel.logOut()
                    },
                )
            }

            walletSwitch?.let { target ->
                WalletSwitchOverlay(
                    target = target,
                    onCovered = { walletViewModel.selectedWallet = target },
                    onFinished = { walletSwitch = null },
                )
            }
        }
    }
}
