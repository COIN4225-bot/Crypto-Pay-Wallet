package com.example.data.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CoinBnbColor
import com.example.ui.theme.CoinBtcColor
import com.example.ui.theme.CoinDemoColor
import com.example.ui.theme.CoinEthColor
import com.example.ui.theme.CoinUsdtColor
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val walletAddress: String = "",
    val photoUrl: String? = null,
    val favoriteCoins: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PriceAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val coin: String = "BTC",
    val direction: String = AlertDirection.ABOVE.name,
    val targetPriceUsdt: Double = 0.0,
    val active: Boolean = true,
    val triggeredCount: Long = 0L,
    @ServerTimestamp val lastTriggeredAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class AlertDirection(val label: String, val symbol: String) {
    ABOVE("Crosses Above", "≥"),
    BELOW("Drops Below", "≤");

    companion object {
        fun fromRaw(raw: String): AlertDirection =
            entries.firstOrNull { it.name == raw } ?: ABOVE
    }
}

@Keep
@IgnoreExtraProperties
data class Wallet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val balance: Double = 0.0,
    val btc: Double = 0.0,
    val eth: Double = 0.0,
    val bnb: Double = 0.0,
    val demoCoin: Double = 0.0,
    val totalBalance: Double = 0.0,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun balanceForCoin(symbol: String): Double = when (symbol) {
        CoinAsset.USDT.symbol -> balance
        CoinAsset.BTC.symbol -> btc
        CoinAsset.ETH.symbol -> eth
        CoinAsset.BNB.symbol -> bnb
        CoinAsset.DEMO_COIN.symbol -> demoCoin
        else -> 0.0
    }

    fun fieldNameForCoin(symbol: String): String = when (symbol) {
        CoinAsset.USDT.symbol -> "balance"
        CoinAsset.BTC.symbol -> "btc"
        CoinAsset.ETH.symbol -> "eth"
        CoinAsset.BNB.symbol -> "bnb"
        CoinAsset.DEMO_COIN.symbol -> "demoCoin"
        else -> "balance"
    }
}

@Keep
@IgnoreExtraProperties
data class CryptoTransaction(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val fromEmail: String = "",
    val toEmail: String = "",
    val amount: Double = 0.0,
    val usdtValue: Double = 0.0,
    val coin: String = "USDT",
    val type: String = "buy",
    val status: String = "completed",
    @ServerTimestamp val timestamp: Timestamp? = null,
    val note: String? = null
)

enum class CoinAsset(
    val symbol: String,
    val displayName: String,
    val shortBadge: String,
    val basePriceUsdt: Double,
    val accentColor: Color
) {
    BTC("BTC", "Bitcoin", "₿", 94250.0, CoinBtcColor),
    ETH("ETH", "Ethereum", "Ξ", 3340.0, CoinEthColor),
    USDT("USDT", "Tether Demo", "₮", 1.0, CoinUsdtColor),
    BNB("BNB", "BNB Chain", "B", 645.0, CoinBnbColor),
    DEMO_COIN("DemoCoin", "DemoCoin", "D", 12.50, CoinDemoColor);

    companion object {
        fun fromSymbol(symbol: String): CoinAsset =
            entries.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } ?: USDT
    }
}

data class MarketCoinTicker(
    val asset: CoinAsset,
    val priceUsdt: Double,
    val changePercent24h: Double,
    val sparkline: List<Double>
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface TradeActionState {
    data object Idle : TradeActionState
    data object Processing : TradeActionState
    data class Success(val message: String, val transaction: CryptoTransaction? = null) : TradeActionState
    data class Error(val message: String) : TradeActionState
}

enum class HistoryFilter(val label: String) {
    ALL("All"),
    BUY("Buy"),
    SELL("Sell"),
    SEND("Send"),
    RECEIVE("Receive")
}

object CryptoFormatters {
    const val INR_PER_USDT = 84.50

    fun formatUsdt(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$${formatter.format(value)}"
    }

    fun formatInr(usdtValue: Double): String {
        val inrValue = usdtValue * INR_PER_USDT
        val formatter = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "₹${formatter.format(inrValue)}"
    }

    fun formatCoinAmount(amount: Double, symbol: String): String {
        val decimals = when (symbol) {
            CoinAsset.BTC.symbol, CoinAsset.ETH.symbol -> 6
            CoinAsset.BNB.symbol -> 4
            else -> 2
        }
        return String.format(Locale.US, "%.${decimals}f", amount)
            .trimEnd('0')
            .trimEnd('.')
            .let { if (it.isEmpty() || it == "-") "0" else it }
    }

    fun formatPrice(priceUsdt: Double, inrMode: Boolean): String {
        val display = if (inrMode) priceUsdt * INR_PER_USDT else priceUsdt
        val prefix = if (inrMode) "₹" else "$"
        val decimals = if (display < 50.0) 2 else 2
        val formatter = NumberFormat.getNumberInstance(if (inrMode) Locale("en", "IN") else Locale.US).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
        return "$prefix${formatter.format(display)}"
    }

    fun formatTimestamp(timestamp: Timestamp?): String {
        val date = (timestamp ?: Timestamp.now()).toDate()
        return SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(date)
    }

    fun maskAddress(address: String): String {
        if (address.length <= 14) return address
        return "${address.take(8)}...${address.takeLast(6)}"
    }
}
