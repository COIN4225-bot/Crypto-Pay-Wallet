package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.data.model.Wallet
import com.example.ui.components.CoinBadge
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
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.WalletViewModel

@Composable
fun TradeScreen(
    viewModel: WalletViewModel
) {
    val walletState by viewModel.walletState.collectAsStateWithLifecycle()
    val tickers by viewModel.marketTickers.collectAsStateWithLifecycle()
    val selectedCoin by viewModel.selectedTradeCoin.collectAsStateWithLifecycle()
    val isBuyMode by viewModel.selectedTradeIsBuy.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    val wallet = (walletState as? UiState.Success)?.data ?: Wallet()
    var inputAmountText by remember { mutableStateOf("") }
    var enterInInr by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val activeCoin = if (!isBuyMode && selectedCoin == CoinAsset.USDT) CoinAsset.BTC else selectedCoin
    val ticker = tickers.firstOrNull { it.asset == activeCoin }
    val unitPriceUsdt = ticker?.priceUsdt ?: activeCoin.basePriceUsdt

    val parsedAmount = inputAmountText.toDoubleOrNull() ?: 0.0
    val buyUsdtAmount = if (enterInInr) parsedAmount / CryptoFormatters.INR_PER_USDT else parsedAmount
    val buyCoinQuantity = if (activeCoin == CoinAsset.USDT) buyUsdtAmount else (buyUsdtAmount / unitPriceUsdt)

    val sellCoinQuantity = parsedAmount
    val sellUsdtProceeds = sellCoinQuantity * unitPriceUsdt

    val buyCoins = listOf(CoinAsset.BTC, CoinAsset.ETH, CoinAsset.USDT, CoinAsset.BNB, CoinAsset.DEMO_COIN)
    val sellCoins = listOf(CoinAsset.BTC, CoinAsset.ETH, CoinAsset.BNB, CoinAsset.DEMO_COIN)

    val availableHolding = wallet.balanceForCoin(activeCoin.symbol)
    val validationError = when {
        inputAmountText.isBlank() -> null
        parsedAmount <= 0.0 -> "Enter an amount greater than 0."
        isBuyMode && activeCoin != CoinAsset.USDT && buyUsdtAmount > wallet.balance ->
            "Insufficient Demo USDT (Available: ${CryptoFormatters.formatUsdt(wallet.balance)})"
        !isBuyMode && sellCoinQuantity > availableHolding + 1e-9 ->
            "Insufficient ${activeCoin.symbol} balance (Available: ${CryptoFormatters.formatCoinAmount(availableHolding, activeCoin.symbol)})"
        else -> null
    }

    val isProcessing = actionState is TradeActionState.Processing
    val canSubmit = parsedAmount > 0.0 && validationError == null && !isProcessing

    if (actionState is TradeActionState.Success) {
        val success = actionState as TradeActionState.Success
        SuccessCelebrationDialog(
            title = if (isBuyMode) "Purchase Completed" else "Sale Completed",
            message = success.message,
            detailLine = success.transaction?.let { tx ->
                "${CryptoFormatters.formatCoinAmount(tx.amount, tx.coin)} ${tx.coin} • ${CryptoFormatters.formatUsdt(tx.usdtValue)}"
            },
            onDismiss = {
                inputAmountText = ""
                viewModel.resetActionState()
            }
        )
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = CardElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = if (isBuyMode) "Confirm Buy Order" else "Confirm Sell Order",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConfirmDetailRow("Asset", "${activeCoin.displayName} (${activeCoin.symbol})")
                    if (isBuyMode) {
                        ConfirmDetailRow(
                            "Order Value",
                            "${CryptoFormatters.formatUsdt(buyUsdtAmount)} (${CryptoFormatters.formatInr(buyUsdtAmount)})"
                        )
                        ConfirmDetailRow(
                            "You Receive",
                            "${CryptoFormatters.formatCoinAmount(buyCoinQuantity, activeCoin.symbol)} ${activeCoin.symbol}"
                        )
                    } else {
                        ConfirmDetailRow(
                            "Selling",
                            "${CryptoFormatters.formatCoinAmount(sellCoinQuantity, activeCoin.symbol)} ${activeCoin.symbol}"
                        )
                        ConfirmDetailRow(
                            "You Receive",
                            "${CryptoFormatters.formatUsdt(sellUsdtProceeds)} Demo USDT"
                        )
                    }
                    ConfirmDetailRow("Market Price", CryptoFormatters.formatPrice(unitPriceUsdt, false))
                    ConfirmDetailRow("Trading Fee", "$0.00 (Demo)")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        if (isBuyMode) {
                            viewModel.buyCrypto(activeCoin.symbol, buyUsdtAmount)
                        } else {
                            viewModel.sellCrypto(activeCoin.symbol, sellCoinQuantity)
                        }
                    },
                    modifier = Modifier.testTag("confirm_trade_dialog_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBuyMode) CryptoGreen else CryptoRed,
                        contentColor = ObsidianDeep
                    )
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Trade Crypto",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        // Buy / Sell Segmented Bar
        item {
            Surface(
                color = CardSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isBuyMode) CryptoGreen else CardSurface)
                            .clickable {
                                inputAmountText = ""
                                viewModel.setTradeMode(true)
                            }
                            .testTag("trade_tab_buy")
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Buy",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isBuyMode) ObsidianDeep else TextSecondary
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isBuyMode) CryptoRed else CardSurface)
                            .clickable {
                                inputAmountText = ""
                                viewModel.setTradeMode(false)
                            }
                            .testTag("trade_tab_sell")
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sell",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (!isBuyMode) ObsidianDeep else TextSecondary
                            )
                        )
                    }
                }
            }
        }

        // Coin selector
        item {
            Text(
                text = "Select Coin",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val list = if (isBuyMode) buyCoins else sellCoins
                list.forEach { coin ->
                    CoinSelectorChip(
                        asset = coin,
                        selected = activeCoin == coin,
                        onClick = {
                            inputAmountText = ""
                            viewModel.selectTradeCoin(coin)
                        }
                    )
                }
            }
        }

        // Price & Balance Summary Card
        item {
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
                    CoinBadge(asset = activeCoin, size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${activeCoin.displayName} (${activeCoin.symbol})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Holding: ${CryptoFormatters.formatCoinAmount(availableHolding, activeCoin.symbol)} ${activeCoin.symbol}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = CryptoFormatters.formatPrice(unitPriceUsdt, false),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = CryptoFormatters.formatPrice(unitPriceUsdt, true),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Amount Input Card
        item {
            Surface(
                color = CardSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBuyMode) {
                                if (enterInInr) "Amount in INR (₹)" else "Amount in USDT ($)"
                            } else {
                                "Quantity in ${activeCoin.symbol}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        if (isBuyMode) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { enterInInr = !enterInInr }
                                    .testTag("toggle_inr_usdt_input"),
                                color = CardElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    text = if (enterInInr) "Switch to USDT" else "Switch to INR",
                                    style = MaterialTheme.typography.labelSmall.copy(color = BinanceGold),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputAmountText,
                        onValueChange = { raw ->
                            if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d*$"))) {
                                inputAmountText = raw
                            }
                        },
                        placeholder = {
                            Text(
                                text = if (isBuyMode) (if (enterInInr) "e.g. 5000" else "e.g. 250") else "e.g. 0.05",
                                color = TextSecondary
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trade_amount_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BinanceGold,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isBuyMode) {
                            val presets = if (enterInInr) listOf("1000", "5000", "25000") else listOf("100", "500", "1000")
                            presets.forEach { preset ->
                                OutlinedButton(
                                    onClick = { inputAmountText = preset },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (enterInInr) "₹$preset" else "$$preset",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary
                                    )
                                }
                            }
                            if (activeCoin != CoinAsset.USDT) {
                                OutlinedButton(
                                    onClick = {
                                        val maxVal = if (enterInInr) wallet.balance * CryptoFormatters.INR_PER_USDT else wallet.balance
                                        inputAmountText = String.format(java.util.Locale.US, "%.2f", maxVal)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BinanceGold.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text("MAX", style = MaterialTheme.typography.labelMedium, color = BinanceGold)
                                }
                            }
                        } else {
                            listOf(0.25 to "25%", 0.50 to "50%", 0.75 to "75%", 1.0 to "MAX").forEach { (pct, label) ->
                                OutlinedButton(
                                    onClick = {
                                        val qty = availableHolding * pct
                                        inputAmountText = CryptoFormatters.formatCoinAmount(qty, activeCoin.symbol)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live quote preview
                    Surface(
                        color = CardElevated,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isBuyMode) {
                                Text(
                                    text = "Available USDT: ${CryptoFormatters.formatUsdt(wallet.balance)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Estimated Receive: ${CryptoFormatters.formatCoinAmount(buyCoinQuantity, activeCoin.symbol)} ${activeCoin.symbol}",
                                    style = MaterialTheme.typography.labelLarge.copy(color = CryptoGreen)
                                )
                            } else {
                                Text(
                                    text = "Available ${activeCoin.symbol}: ${CryptoFormatters.formatCoinAmount(availableHolding, activeCoin.symbol)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Estimated Proceeds: ${CryptoFormatters.formatUsdt(sellUsdtProceeds)} (${CryptoFormatters.formatInr(sellUsdtProceeds)})",
                                    style = MaterialTheme.typography.labelLarge.copy(color = BinanceGold)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (validationError != null) {
            item {
                Text(
                    text = validationError,
                    style = MaterialTheme.typography.bodyMedium.copy(color = CryptoRed),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        if (actionState is TradeActionState.Error) {
            val err = actionState as TradeActionState.Error
            item {
                Surface(
                    color = CryptoRedSoft,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err.message,
                        style = MaterialTheme.typography.bodyMedium.copy(color = CryptoRed),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Button(
                onClick = { showConfirmDialog = true },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_trade_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBuyMode) CryptoGreen else CryptoRed,
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
                    Text(
                        text = if (isBuyMode) "Buy ${activeCoin.symbol}" else "Sell ${activeCoin.symbol}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        if (isBuyMode) {
            item {
                OutlinedButton(
                    onClick = { viewModel.claimDemoUsdt(5000.0) },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("faucet_claim_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BinanceGold.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "+ Claim 5,000 Demo USDT",
                        style = MaterialTheme.typography.labelLarge.copy(color = BinanceGold)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun ConfirmDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    }
}
