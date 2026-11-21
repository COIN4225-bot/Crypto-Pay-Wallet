package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.BiometricAuthManager
import com.example.auth.BiometricAvailability
import com.example.auth.ExternalSystemFlowTracker
import com.example.auth.authStateFlow
import com.example.data.model.CoinAsset
import com.example.data.model.CryptoFormatters
import com.example.data.repository.CryptoWalletRepository
import com.example.data.repository.MarketPriceEngine
import com.example.notifications.PriceAlertNotifier
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.TradeScreen
import com.example.ui.screens.TransferScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.BinanceGold
import com.example.ui.theme.BinanceGoldSoft
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CryptoPayTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.WalletViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object Routes {
    const val HOME = "home"
    const val TRADE = "trade"
    const val WALLET = "wallet"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val TRANSFER = "transfer"
    const val RECEIVE = "receive"
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomTabs = listOf(
    BottomNavTab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavTab(Routes.TRADE, "Trade", Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz),
    BottomNavTab(Routes.WALLET, "Wallet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    BottomNavTab(Routes.HISTORY, "History", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    BottomNavTab(Routes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

class MainActivity : FragmentActivity() {

    private var isAppLocked by mutableStateOf(false)
    private var biometricError by mutableStateOf<String?>(null)
    private var isPromptInFlight = false
    private var hasHandledInitialLaunch = false
    private var wentToBackground = false

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (!hasHandledInitialLaunch) {
                hasHandledInitialLaunch = true
                lockAndPromptIfAvailable()
            } else if (wentToBackground) {
                wentToBackground = false
                lockAndPromptIfAvailable()
            }
            ExternalSystemFlowTracker.endExternalFlow()
        }

        override fun onStop(owner: LifecycleOwner) {
            // Do not lock when user is inside Google Sign-In or Android Share system sheets
            if (!ExternalSystemFlowTracker.isExternalFlowActive && !isPromptInFlight) {
                wentToBackground = true
                if (BiometricAuthManager.checkAvailability(this@MainActivity) is BiometricAvailability.Available) {
                    isAppLocked = true
                    biometricError = null
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState != null) {
            // Configuration change (e.g., rotation): preserve unlocked state
            hasHandledInitialLaunch = true
            isAppLocked = savedInstanceState.getBoolean(KEY_APP_LOCKED, false)
        } else {
            if (BiometricAuthManager.checkAvailability(this) is BiometricAvailability.Available) {
                isAppLocked = true
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        PriceAlertNotifier.ensureNotificationChannel(this)

        setContent {
            CryptoPayTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppContent()
                    if (isAppLocked) {
                        BiometricLockScreen(
                            errorMessage = biometricError,
                            onUnlockClick = { triggerBiometricPrompt() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ExternalSystemFlowTracker.endExternalFlow()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_APP_LOCKED, isAppLocked)
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        super.onDestroy()
    }

    private fun lockAndPromptIfAvailable() {
        when (BiometricAuthManager.checkAvailability(this)) {
            BiometricAvailability.Available -> {
                isAppLocked = true
                biometricError = null
                window.decorView.post { triggerBiometricPrompt() }
            }
            is BiometricAvailability.Unavailable -> {
                // Allow app access if device has no biometrics/PIN enrolled
                isAppLocked = false
                biometricError = null
            }
        }
    }

    private fun triggerBiometricPrompt() {
        if (isPromptInFlight || !isAppLocked || isFinishing || isDestroyed) return
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        if (BiometricAuthManager.checkAvailability(this) !is BiometricAvailability.Available) {
            isAppLocked = false
            return
        }
        isPromptInFlight = true
        biometricError = null
        BiometricAuthManager.authenticate(
            activity = this,
            onSuccess = {
                isPromptInFlight = false
                isAppLocked = false
                biometricError = null
            },
            onError = { message ->
                isPromptInFlight = false
                isAppLocked = true
                biometricError = message
            },
            onCancelled = {
                isPromptInFlight = false
                isAppLocked = true
                biometricError = "Authentication required to unlock CryptoPay Wallet."
            }
        )
    }

    private companion object {
        const val KEY_APP_LOCKED = "key_app_locked"
    }
}

@Composable
fun AppContent(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ })
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedWalletApp(currentUserId = uid)
        }
    }
}

@Composable
private fun AuthenticatedWalletApp(currentUserId: String) {
    val walletViewModel: WalletViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                WalletViewModel(
                    repository = CryptoWalletRepository(db),
                    priceEngine = MarketPriceEngine(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val context = LocalContext.current
    val globalSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(walletViewModel) {
        walletViewModel.alertEvents.collect { event ->
            PriceAlertNotifier.sendPriceAlertNotification(
                context = context,
                alertId = event.alertId,
                coinSymbol = event.coinSymbol,
                direction = event.direction,
                targetPriceUsdt = event.targetPriceUsdt,
                currentPriceUsdt = event.currentPriceUsdt
            )
            globalSnackbarHostState.showSnackbar(
                "Alert: ${event.coinSymbol} ${event.direction.symbol} ${CryptoFormatters.formatUsdt(event.targetPriceUsdt)} (Now ${CryptoFormatters.formatUsdt(event.currentPriceUsdt)})"
            )
        }
    }

    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = globalSnackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = CardSurface,
                    tonalElevation = 0.dp
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BinanceGold,
                                selectedTextColor = BinanceGold,
                                indicatorColor = BinanceGoldSoft,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_${tab.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = walletViewModel,
                    currentUid = currentUserId,
                    onQuickBuy = { coin ->
                        walletViewModel.selectTradeCoin(coin, isBuy = true)
                        navigateTopTab(Routes.TRADE)
                    },
                    onQuickSell = {
                        walletViewModel.selectTradeCoin(CoinAsset.BTC, isBuy = false)
                        navigateTopTab(Routes.TRADE)
                    },
                    onQuickSend = {
                        navController.navigate(Routes.TRANSFER)
                    },
                    onQuickReceive = {
                        navController.navigate(Routes.RECEIVE)
                    },
                    onSeeAllHistory = {
                        navigateTopTab(Routes.HISTORY)
                    }
                )
            }

            composable(Routes.TRADE) {
                TradeScreen(viewModel = walletViewModel)
            }

            composable(Routes.WALLET) {
                WalletScreen(
                    viewModel = walletViewModel,
                    onNavigateTransfer = { navController.navigate(Routes.TRANSFER) },
                    onNavigateReceive = { navController.navigate(Routes.RECEIVE) },
                    onTradeCoin = { coin ->
                        walletViewModel.selectTradeCoin(coin, isBuy = true)
                        navigateTopTab(Routes.TRADE)
                    }
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    viewModel = walletViewModel,
                    currentUid = currentUserId
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    viewModel = walletViewModel,
                    currentUid = currentUserId,
                    onSignedOut = { /* AuthStateListener returns cleanly to AuthScreen */ }
                )
            }

            composable(Routes.TRANSFER) {
                TransferScreen(
                    viewModel = walletViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.RECEIVE) {
                ReceiveScreen(
                    viewModel = walletViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
