package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CoinAsset
import com.example.data.model.CryptoFormatters
import com.example.data.model.UiState
import com.example.data.model.Wallet
import com.example.ui.components.CoinBadge
import com.example.ui.theme.BinanceGold
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.WalletViewModel

@Composable
fun WalletScreen(
    viewModel: WalletViewModel,
    onNavigateTransfer: () -> Unit,
    onNavigateReceive: () -> Unit,
    onTradeCoin: (CoinAsset) -> Unit
) {
    val walletState by viewModel.walletState.collectAsStateWithLifecycle()
    val tickers by viewModel.marketTickers.collectAsStateWithLifecycle()
    val livePortfolioUsdt by viewModel.livePortfolioUsdt.collectAsStateWithLifecycle()
    val balanceHidden by viewModel.balanceHidden.collectAsStateWithLifecycle()
    val inrMode by viewModel.inrCurrencyMode.collectAsStateWithLifecycle()

    val wallet = (walletState as? UiState.Success)?.data ?: Wallet()
    val priceMap = tickers.associate { it.asset.symbol to it.priceUsdt }

    val holdings = CoinAsset.entries.map { asset ->
        val qty = wallet.balanceForCoin(asset.symbol)
        val unitPrice = priceMap[asset.symbol] ?: asset.basePriceUsdt
        val usdtVal = qty * unitPrice
        Triple(asset, qty, usdtVal)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "My Wallet",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        // Net Worth + Send / Receive CTA Card
        item {
            Surface(
                color = CardSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Estimated Asset Value",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (walletState is UiState.Loading) {
                        CircularProgressIndicator(color = BinanceGold, modifier = Modifier.size(28.dp))
                    } else {
                        Text(
                            text = when {
                                balanceHidden -> "••••••••"
                                inrMode -> CryptoFormatters.formatInr(livePortfolioUsdt)
                                else -> CryptoFormatters.formatUsdt(livePortfolioUsdt)
                            },
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Portfolio Allocation Bar
                    if (livePortfolioUsdt > 0.0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(100.dp)),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            holdings.filter { it.third > 0.01 }.forEach { (asset, _, value) ->
                                val weight = (value / livePortfolioUsdt).toFloat().coerceAtLeast(0.03f)
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .background(asset.accentColor)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateTransfer,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wallet_send_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BinanceGold,
                                contentColor = ObsidianDeep
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send P2P",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("P2P Send", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onNavigateReceive,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wallet_receive_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QrCode2,
                                contentDescription = "Receive QR",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Receive", color = CyberCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Asset Balances",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        items(holdings, key = { it.first.symbol }) { (asset, qty, usdtVal) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onTradeCoin(asset) },
                color = CardSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoinBadge(asset = asset, size = 42.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = asset.symbol,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = asset.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (balanceHidden) "••••" else CryptoFormatters.formatCoinAmount(qty, asset.symbol),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = when {
                                balanceHidden -> "••••"
                                inrMode -> CryptoFormatters.formatInr(usdtVal)
                                else -> CryptoFormatters.formatUsdt(usdtVal)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}
