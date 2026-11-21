package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CoinAsset
import com.example.data.model.CryptoFormatters
import com.example.data.model.TradeActionState
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.Wallet
import com.example.ui.components.CoinSelectorChip
import com.example.ui.components.SuccessCelebrationDialog
import com.example.ui.theme.BinanceGold
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardElevated
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoGreenSoft
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.CryptoRedSoft
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.WalletViewModel

@Composable
fun TransferScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit
) {
    val walletState by viewModel.walletState.collectAsStateWithLifecycle()
    val recipientState by viewModel.recipientSearchState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    val wallet = (walletState as? UiState.Success)?.data ?: Wallet()

    var recipientQuery by remember { mutableStateOf("") }
    var selectedCoin by remember { mutableStateOf(CoinAsset.USDT) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetRecipientSearch()
            viewModel.resetActionState()
        }
    }

    val verifiedRecipient: UserProfile? = (recipientState as? UiState.Success)?.data
    val availableBalance = wallet.balanceForCoin(selectedCoin.symbol)
    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0

    val balanceError = when {
        amountText.isBlank() -> null
        parsedAmount <= 0.0 -> "Enter an amount greater than 0."
        parsedAmount > availableBalance + 1e-9 -> "Insufficient ${selectedCoin.symbol} balance."
        else -> null
    }

    val isProcessing = actionState is TradeActionState.Processing
    val canTransfer = verifiedRecipient != null && parsedAmount > 0.0 && balanceError == null && !isProcessing

    if (actionState is TradeActionState.Success) {
        val success = actionState as TradeActionState.Success
        SuccessCelebrationDialog(
            title = "Transfer Successful!",
            message = success.message,
            detailLine = success.transaction?.let { tx ->
                "${CryptoFormatters.formatCoinAmount(tx.amount, tx.coin)} ${tx.coin} → ${tx.toEmail}"
            },
            onDismiss = {
                amountText = ""
                noteText = ""
                viewModel.resetActionState()
                onBack()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("transfer_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "P2P Crypto Transfer",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step 1: Recipient Search
            item {
                Surface(
                    color = CardSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "1. Find Recipient",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Search by Email, Wallet Address (0x...), or User UID",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = recipientQuery,
                                onValueChange = {
                                    recipientQuery = it
                                    if (verifiedRecipient != null) {
                                        viewModel.resetRecipientSearch()
                                    }
                                },
                                placeholder = { Text("email / 0x... / uid", color = TextSecondary) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("recipient_search_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            Button(
                                onClick = { viewModel.lookupRecipient(recipientQuery) },
                                enabled = recipientQuery.isNotBlank() && recipientState !is UiState.Loading,
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("recipient_lookup_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberCyan,
                                    contentColor = ObsidianDeep
                                )
                            ) {
                                if (recipientState is UiState.Loading) {
                                    CircularProgressIndicator(
                                        color = ObsidianDeep,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = "Find User"
                                    )
                                }
                            }
                        }

                        if (recipientState is UiState.Error) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = CryptoRedSoft,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = (recipientState as UiState.Error).message,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = CryptoRed),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (verifiedRecipient != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = CryptoGreenSoft,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(CryptoGreen.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Verified Recipient",
                                            tint = CryptoGreen
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = verifiedRecipient.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = verifiedRecipient.email,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = CryptoFormatters.maskAddress(verifiedRecipient.walletAddress),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CryptoGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step 2: Select Coin & Amount
            item {
                Surface(
                    color = CardSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "2. Asset & Amount",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CoinAsset.entries.forEach { coin ->
                                CoinSelectorChip(
                                    asset = coin,
                                    selected = selectedCoin == coin,
                                    onClick = {
                                        selectedCoin = coin
                                        amountText = ""
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Available: ${CryptoFormatters.formatCoinAmount(availableBalance, selectedCoin.symbol)} ${selectedCoin.symbol}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            OutlinedButton(
                                onClick = {
                                    amountText = CryptoFormatters.formatCoinAmount(availableBalance, selectedCoin.symbol)
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, BinanceGold.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("MAX", style = MaterialTheme.typography.labelSmall, color = BinanceGold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { raw ->
                                if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    amountText = raw
                                }
                            },
                            placeholder = { Text("0.00 ${selectedCoin.symbol}", color = TextSecondary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transfer_amount_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BinanceGold,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        if (balanceError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = balanceError,
                                style = MaterialTheme.typography.bodyMedium.copy(color = CryptoRed)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { if (it.length <= 140) noteText = it },
                            placeholder = { Text("Optional note (e.g. Dinner split)", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transfer_note_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BinanceGold,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                }
            }

            if (actionState is TradeActionState.Error) {
                item {
                    Surface(
                        color = CryptoRedSoft,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = (actionState as TradeActionState.Error).message,
                            style = MaterialTheme.typography.bodyMedium.copy(color = CryptoRed),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        verifiedRecipient?.let { target ->
                            viewModel.transferP2P(
                                receiver = target,
                                coinSymbol = selectedCoin.symbol,
                                amount = parsedAmount,
                                note = noteText
                            )
                        }
                    },
                    enabled = canTransfer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("confirm_transfer_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BinanceGold,
                        contentColor = ObsidianDeep
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = ObsidianDeep,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Now",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send Instantly",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
