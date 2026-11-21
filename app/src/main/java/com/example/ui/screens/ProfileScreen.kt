package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.auth.BiometricAuthManager
import com.example.auth.BiometricAvailability
import com.example.auth.signOutUser
import com.example.data.model.AlertDirection
import com.example.data.model.CoinAsset
import com.example.data.model.CryptoFormatters
import com.example.data.model.PriceAlert
import com.example.data.model.TradeActionState
import com.example.data.model.UiState
import com.example.notifications.PriceAlertNotifier
import com.example.ui.components.CoinBadge
import com.example.ui.theme.BinanceGold
import com.example.ui.theme.BinanceGoldSoft
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardElevated
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoGreenSoft
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.WalletViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ProfileScreen(
    viewModel: WalletViewModel,
    currentUid: String,
    onSignedOut: () -> Unit
) {
    val userState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val alertsState by viewModel.priceAlertsState.collectAsStateWithLifecycle()
    val tickers by viewModel.marketTickers.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    val profile = (userState as? UiState.Success)?.data
    val priceAlerts = (alertsState as? UiState.Success)?.data ?: emptyList()
    val favoriteCoins = profile?.favoriteCoins ?: emptyList()
    val firebaseUser = Firebase.auth.currentUser

    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasNotifPermission by remember {
        mutableStateOf(PriceAlertNotifier.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) "Price alert notifications enabled!"
                else "Notification permission denied. Enable in system settings."
            )
        }
    }

    var selectedAlertCoin by remember { mutableStateOf(CoinAsset.BTC) }
    var selectedDirection by remember { mutableStateOf(AlertDirection.ABOVE) }
    var targetPriceInput by remember { mutableStateOf("") }

    val displayName = profile?.name ?: firebaseUser?.displayName ?: "CryptoPay User"
    val email = profile?.email ?: firebaseUser?.email ?: "Authenticated User"
    val walletAddress = profile?.walletAddress ?: "Generating..."
    val photoUrl = profile?.photoUrl ?: firebaseUser?.photoUrl?.toString()

    if (actionState is TradeActionState.Success) {
        val msg = (actionState as TradeActionState.Success).message
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.resetActionState()
        }
    } else if (actionState is TradeActionState.Error) {
        val msg = (actionState as TradeActionState.Error).message
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.resetActionState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Profile & Alerts",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            // User Identity Card
            item {
                Surface(
                    color = CardSurface,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "User Profile Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(BinanceGold),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayName.firstOrNull()?.uppercase() ?: "C",
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        color = ObsidianDeep,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = CryptoGreenSoft,
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = "Google Verified",
                                    tint = CryptoGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Google Auth Verified",
                                    style = MaterialTheme.typography.labelSmall.copy(color = CryptoGreen)
                                )
                            }
                        }
                    }
                }
            }

            // UID & Wallet Address Card
            item {
                Surface(
                    color = CardSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CopyableIdentifierRow(
                            label = "Firebase UID (for P2P Transfer)",
                            value = currentUid,
                            tag = "copy_uid_button",
                            onCopy = {
                                clipboard.setText(AnnotatedString(currentUid))
                                scope.launch { snackbarHostState.showSnackbar("UID copied to clipboard!") }
                            }
                        )
                        CopyableIdentifierRow(
                            label = "Wallet Address",
                            value = walletAddress,
                            tag = "copy_profile_address_button",
                            onCopy = {
                                clipboard.setText(AnnotatedString(walletAddress))
                                scope.launch { snackbarHostState.showSnackbar("Wallet address copied!") }
                            }
                        )
                    }
                }
            }

            // Favorite Coins & Price Threshold Alerts Section
            item {
                val livePrice = tickers.firstOrNull { it.asset == selectedAlertCoin }?.priceUsdt
                    ?: selectedAlertCoin.basePriceUsdt

                Surface(
                    color = CardSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = "Price Alerts",
                                    tint = BinanceGold,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Favorite Coins & Alerts",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            viewModel.triggerSampleNotification(selectedAlertCoin.symbol)
                                        }
                                    }
                                    .testTag("test_price_alert_button"),
                                color = BinanceGoldSoft,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Test Push",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = BinanceGold,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Surface(
                                color = BinanceGoldSoft,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BinanceGold.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Allow notifications to receive live price alerts.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        modifier = Modifier.testTag("grant_notifications_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = BinanceGold,
                                            contentColor = ObsidianDeep
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Enable", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Favorite Coins Chips
                        Text(
                            text = "Favorite Coins",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(CoinAsset.BTC, CoinAsset.ETH, CoinAsset.BNB, CoinAsset.DEMO_COIN).forEach { coin ->
                                val isFav = coin.symbol in favoriteCoins
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.toggleFavoriteCoin(coin.symbol)
                                            selectedAlertCoin = coin
                                        }
                                        .testTag("profile_fav_${coin.symbol}"),
                                    color = if (isFav) BinanceGoldSoft else CardElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isFav) BinanceGold else CardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                            contentDescription = coin.symbol,
                                            tint = if (isFav) BinanceGold else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = coin.symbol,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color = if (isFav) BinanceGold else TextPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Alert Coin Selector
                        Text(
                            text = "New Threshold Alert",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(CoinAsset.BTC, CoinAsset.ETH, CoinAsset.BNB, CoinAsset.DEMO_COIN).forEach { coin ->
                                val selected = selectedAlertCoin == coin
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedAlertCoin = coin
                                            targetPriceInput = ""
                                        }
                                        .testTag("alert_coin_${coin.symbol}"),
                                    color = if (selected) coin.accentColor.copy(alpha = 0.18f) else CardElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (selected) coin.accentColor else CardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CoinBadge(asset = coin, size = 20.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = coin.symbol,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // Direction Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AlertDirection.entries.forEach { dir ->
                                val selected = selectedDirection == dir
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedDirection = dir }
                                        .testTag("alert_dir_${dir.name.lowercase()}"),
                                    color = if (selected) {
                                        if (dir == AlertDirection.ABOVE) CryptoGreenSoft else CryptoRed.copy(alpha = 0.18f)
                                    } else CardElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) (if (dir == AlertDirection.ABOVE) CryptoGreen else CryptoRed) else CardBorder
                                    )
                                ) {
                                    Text(
                                        text = "${dir.label} (${dir.symbol})",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = if (selected) TextPrimary else TextSecondary
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = targetPriceInput,
                            onValueChange = { raw ->
                                if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    targetPriceInput = raw
                                }
                            },
                            label = { Text("Target Price (USDT) • Live: ${CryptoFormatters.formatUsdt(livePrice)}") },
                            placeholder = { Text(String.format(Locale.US, "%.2f", livePrice * 1.01), color = TextSecondary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("alert_target_price_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BinanceGold,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        // Quick target price presets relative to live price
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(-0.005 to "-0.5%", 0.005 to "+0.5%", 0.02 to "+2%").forEach { (delta, label) ->
                                OutlinedButton(
                                    onClick = {
                                        val suggested = livePrice * (1.0 + delta)
                                        selectedDirection = if (delta >= 0) AlertDirection.ABOVE else AlertDirection.BELOW
                                        targetPriceInput = String.format(Locale.US, "%.2f", suggested)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = BinanceGold)
                                }
                            }
                        }

                        val parsedTarget = targetPriceInput.toDoubleOrNull() ?: 0.0
                        Button(
                            onClick = {
                                if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.createPriceAlert(
                                    coinSymbol = selectedAlertCoin.symbol,
                                    direction = selectedDirection,
                                    targetPriceUsdt = parsedTarget
                                )
                                targetPriceInput = ""
                            },
                            enabled = parsedTarget > 0.0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_price_alert_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BinanceGold,
                                contentColor = ObsidianDeep
                            )
                        ) {
                            Text("Save Price Alert", fontWeight = FontWeight.Bold)
                        }

                        if (priceAlerts.isNotEmpty()) {
                            Text(
                                text = "Configured Alerts (${priceAlerts.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                priceAlerts.forEach { alert ->
                                    PriceAlertItemRow(
                                        alert = alert,
                                        onToggleActive = { active -> viewModel.togglePriceAlertActive(alert.id, active) },
                                        onDelete = { viewModel.deletePriceAlert(alert.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Biometric App Lock Status Card
            item {
                val availability = remember(context) { BiometricAuthManager.checkAvailability(context) }
                val isActive = availability is BiometricAvailability.Available
                Surface(
                    color = CardSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isActive) CryptoGreenSoft else BinanceGold.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = "Biometric Lock Status",
                                tint = if (isActive) CryptoGreen else BinanceGold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric App Lock",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                text = when (availability) {
                                    BiometricAvailability.Available -> "Active on app launch and background return"
                                    is BiometricAvailability.Unavailable -> availability.reason
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.claimDemoUsdt(5000.0) },
                    enabled = actionState !is TradeActionState.Processing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("profile_faucet_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BinanceGold.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "+ Claim 5,000 Demo USDT",
                        style = MaterialTheme.typography.labelLarge.copy(color = BinanceGold)
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = onSignedOut
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CryptoRed.copy(alpha = 0.16f),
                        contentColor = CryptoRed
                    ),
                    border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign Out",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun PriceAlertItemRow(
    alert: PriceAlert,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val direction = AlertDirection.fromRaw(alert.direction)
    val coinAsset = CoinAsset.fromSymbol(alert.coin)
    Surface(
        color = CardElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoinBadge(asset = coinAsset, size = 32.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${alert.coin} ${direction.symbol} ${CryptoFormatters.formatUsdt(alert.targetPriceUsdt)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Text(
                    text = if (alert.active) "Armed • Watching live price" else "Triggered ${alert.triggeredCount}x (Tap switch to re-arm)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (alert.active) CryptoGreen else TextSecondary
                )
            }
            Switch(
                checked = alert.active,
                onCheckedChange = onToggleActive,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianDeep,
                    checkedTrackColor = CryptoGreen
                )
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_alert_${alert.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete Alert",
                    tint = CryptoRed
                )
            }
        }
    }
}

@Composable
private fun CopyableIdentifierRow(
    label: String,
    value: String,
    tag: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(color = CyberCyan)
            )
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier
                .size(48.dp)
                .testTag(tag)
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy $label",
                tint = BinanceGold,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
