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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CoinAsset
import com.example.data.model.CryptoFormatters
import com.example.data.model.CryptoTransaction
import com.example.data.model.MarketCoinTicker
import com.example.data.model.UiState
import com.example.ui.components.ChangePercentPill
import com.example.ui.components.CoinBadge
import com.example.ui.components.MiniSparkline
import com.example.ui.components.PortfolioTrendChartCard
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.BinanceGold
import com.example.ui.theme.BinanceGoldSoft
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardElevated
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.CryptoRedSoft
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.WalletViewModel

@Composable
fun HomeScreen(
    viewModel: WalletViewModel,
    currentUid: String,
    onQuickBuy: (CoinAsset) -> Unit,
    onQuickSell: () -> Unit,
    onQuickSend: () -> Unit,
    onQuickReceive: () -> Unit,
    onSeeAllHistory: () -> Unit
) {
    val userState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val walletState by viewModel.walletState.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val tickers by viewModel.marketTickers.collectAsStateWithLifecycle()
    val livePortfolioUsdt by viewModel.livePortfolioUsdt.collectAsStateWithLifecycle()
    val portfolioTrend7d by viewModel.portfolioTrend7d.collectAsStateWithLifecycle()
    val balanceHidden by viewModel.balanceHidden.collectAsStateWithLifecycle()
    val inrMode by viewModel.inrCurrencyMode.collectAsStateWithLifecycle()
    val initError by viewModel.initError.collectAsStateWithLifecycle()

    val userProfile = (userState as? UiState.Success)?.data
    val displayName = userProfile?.name?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Trader"
    val favoriteCoins = userProfile?.favoriteCoins ?: emptyList()
    val recentTransactions = ((transactionsState as? UiState.Success)?.data ?: emptyList()).take(4)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Welcome back,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable { viewModel.toggleCurrencyMode() }
                        .testTag("currency_toggle_chip"),
                    color = CardElevated,
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text(
                        text = if (inrMode) "INR ₹" else "USDT $",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = BinanceGold,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (initError != null) {
            item {
                Surface(
                    color = CryptoRedSoft,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = initError ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(color = CryptoRed),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.initializeWalletForCurrentUser() }) {
                            Text("Retry", color = BinanceGold)
                        }
                    }
                }
            }
        }

        // Total Balance Hero Card
        item {
            TotalBalanceCard(
                totalUsdt = livePortfolioUsdt,
                demoUsdtLiquid = (walletState as? UiState.Success)?.data?.balance ?: 0.0,
                isLoading = walletState is UiState.Loading,
                balanceHidden = balanceHidden,
                inrMode = inrMode,
                onToggleVisibility = { viewModel.toggleBalanceVisibility() }
            )
        }

        // 7-Day Portfolio Valuation Trend Line
        item {
            PortfolioTrendChartCard(
                trend = portfolioTrend7d,
                isLoading = walletState is UiState.Loading,
                balanceHidden = balanceHidden,
                inrMode = inrMode
            )
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionTile(
                    label = "Buy",
                    icon = Icons.Filled.Add,
                    accent = CryptoGreen,
                    tag = "quick_buy_button",
                    onClick = { onQuickBuy(CoinAsset.BTC) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                QuickActionTile(
                    label = "Sell",
                    icon = Icons.Filled.Sell,
                    accent = BinanceGold,
                    tag = "quick_sell_button",
                    onClick = onQuickSell,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                QuickActionTile(
                    label = "Send",
                    icon = Icons.AutoMirrored.Filled.Send,
                    accent = CyberCyan,
                    tag = "quick_send_button",
                    onClick = onQuickSend,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                QuickActionTile(
                    label = "Receive",
                    icon = Icons.Filled.QrCode2,
                    accent = TextPrimary,
                    tag = "quick_receive_button",
                    onClick = onQuickReceive,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Live Markets Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Markets",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Live Simulation",
                    style = MaterialTheme.typography.labelSmall,
                    color = CryptoGreen
                )
            }
        }

        items(tickers.take(5), key = { it.asset.symbol }) { ticker ->
            MarketTickerRow(
                ticker = ticker,
                inrMode = inrMode,
                isFavorite = ticker.asset.symbol in favoriteCoins,
                onToggleFavorite = { viewModel.toggleFavoriteCoin(ticker.asset.symbol) },
                onClick = { onQuickBuy(ticker.asset) }
            )
        }

        // Recent Transactions Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                TextButton(onClick = onSeeAllHistory) {
                    Text("See All", color = BinanceGold)
                }
            }
        }

        if (transactionsState is UiState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BinanceGold, modifier = Modifier.size(28.dp))
                }
            }
        } else if (recentTransactions.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CardSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap Buy or Send to start trading with your 10,000 Demo USDT.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(recentTransactions, key = { it.id }) { tx ->
                TransactionItemCard(transaction = tx, currentUid = currentUid)
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun TotalBalanceCard(
    totalUsdt: Double,
    demoUsdtLiquid: Double,
    isLoading: Boolean,
    balanceHidden: Boolean,
    inrMode: Boolean,
    onToggleVisibility: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, BinanceGold.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1F232C), Color(0xFF131720), Color(0xFF1A1710))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Total Portfolio Balance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onToggleVisibility,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toggle_balance_visibility")
                        ) {
                            Icon(
                                imageVector = if (balanceHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (balanceHidden) "Show balance" else "Hide balance",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        color = BinanceGoldSoft,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "DEMO MAINNET",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = BinanceGold,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        color = BinanceGold,
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    val primaryText = when {
                        balanceHidden -> "••••••••"
                        inrMode -> CryptoFormatters.formatInr(totalUsdt)
                        else -> CryptoFormatters.formatUsdt(totalUsdt)
                    }
                    val secondaryText = when {
                        balanceHidden -> "Available USDT: ••••"
                        inrMode -> "≈ ${CryptoFormatters.formatUsdt(totalUsdt)} • Liquid: ${CryptoFormatters.formatUsdt(demoUsdtLiquid)}"
                        else -> "≈ ${CryptoFormatters.formatInr(totalUsdt)} • Liquid: ${CryptoFormatters.formatUsdt(demoUsdtLiquid)}"
                    }
                    Text(
                        text = primaryText,
                        style = MaterialTheme.typography.displayLarge.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun MarketTickerRow(
    ticker: MarketCoinTicker,
    inrMode: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("ticker_${ticker.asset.symbol}"),
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoinBadge(asset = ticker.asset, size = 38.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ticker.asset.symbol,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = ticker.asset.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            MiniSparkline(
                points = ticker.sparkline,
                isPositive = ticker.changePercent24h >= 0.0,
                modifier = Modifier
                    .width(54.dp)
                    .height(24.dp)
                    .padding(end = 8.dp)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CryptoFormatters.formatPrice(ticker.priceUsdt, inrMode),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                ChangePercentPill(changePercent = ticker.changePercent24h)
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("fav_${ticker.asset.symbol}")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (isFavorite) "Remove ${ticker.asset.symbol} from favorites" else "Add ${ticker.asset.symbol} to favorites",
                    tint = if (isFavorite) BinanceGold else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
