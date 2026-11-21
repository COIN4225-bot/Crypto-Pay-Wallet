package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class PortfolioTrendPoint(
    val dayOffsetFromToday: Int,
    val dayLabel: String,
    val fullDateLabel: String,
    val valueUsdt: Double,
    val isToday: Boolean
)

data class PortfolioTrendSummary(
    val points: List<PortfolioTrendPoint>,
    val changeUsdt: Double,
    val changePercent: Double,
    val highUsdt: Double,
    val lowUsdt: Double
) {
    companion object {
        val EMPTY = PortfolioTrendSummary(
            points = emptyList(),
            changeUsdt = 0.0,
            changePercent = 0.0,
            highUsdt = 0.0,
            lowUsdt = 0.0
        )
    }
}

object PortfolioTrendCalculator {

    // Stable 7-day daily relative price multipliers (index 0 = 6 days ago ... index 6 = today baseline 1.0)
    private val historicalPriceMultipliers: Map<CoinAsset, List<Double>> = mapOf(
        CoinAsset.BTC to listOf(0.952, 0.964, 0.959, 0.974, 0.985, 0.993, 1.000),
        CoinAsset.ETH to listOf(0.946, 0.955, 0.968, 0.962, 0.978, 0.991, 1.000),
        CoinAsset.BNB to listOf(1.018, 1.012, 1.024, 1.015, 1.008, 1.003, 1.000),
        CoinAsset.DEMO_COIN to listOf(0.896, 0.918, 0.912, 0.944, 0.962, 0.984, 1.000),
        CoinAsset.USDT to listOf(1.000, 1.000, 1.000, 1.000, 1.000, 1.000, 1.000)
    )

    fun calculate7DayTrend(
        wallet: Wallet?,
        transactions: List<CryptoTransaction>,
        tickers: List<MarketCoinTicker>,
        currentUid: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PortfolioTrendSummary {
        if (wallet == null) return PortfolioTrendSummary.EMPTY

        val livePrices = tickers.associate { it.asset to it.priceUsdt }
        val dayBoundaries = buildDayEndMillis(nowMillis) // index 0 = 6 days ago end, index 6 = nowMillis
        val dayLabelFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())

        // Start from current balances and step backward day by day
        var usdt = wallet.balance
        var btc = wallet.btc
        var eth = wallet.eth
        var bnb = wallet.bnb
        var demoCoin = wallet.demoCoin

        // Transactions sorted newest-first so we can reverse them cleanly across day boundaries
        val sortedDesc = transactions
            .filter { it.status.equals("completed", ignoreCase = true) }
            .sortedByDescending { it.timestamp?.toDate()?.time ?: nowMillis }

        var txCursor = 0
        val pointsMutable = ArrayList<PortfolioTrendPoint>(7)

        for (dayIndex in 6 downTo 0) {
            val dayEndMs = dayBoundaries[dayIndex]

            // Reverse all transactions that happened AFTER this day's cutoff
            while (txCursor < sortedDesc.size) {
                val tx = sortedDesc[txCursor]
                val txTimeMs = tx.timestamp?.toDate()?.time ?: nowMillis
                if (txTimeMs <= dayEndMs) break

                when (tx.type.lowercase(Locale.US)) {
                    "buy" -> {
                        val asset = CoinAsset.fromSymbol(tx.coin)
                        if (asset == CoinAsset.USDT) {
                            usdt = (usdt - tx.amount).coerceAtLeast(0.0)
                        } else {
                            usdt += tx.usdtValue
                            when (asset) {
                                CoinAsset.BTC -> btc = (btc - tx.amount).coerceAtLeast(0.0)
                                CoinAsset.ETH -> eth = (eth - tx.amount).coerceAtLeast(0.0)
                                CoinAsset.BNB -> bnb = (bnb - tx.amount).coerceAtLeast(0.0)
                                CoinAsset.DEMO_COIN -> demoCoin = (demoCoin - tx.amount).coerceAtLeast(0.0)
                                CoinAsset.USDT -> Unit
                            }
                        }
                    }
                    "sell" -> {
                        val asset = CoinAsset.fromSymbol(tx.coin)
                        usdt = (usdt - tx.usdtValue).coerceAtLeast(0.0)
                        when (asset) {
                            CoinAsset.BTC -> btc += tx.amount
                            CoinAsset.ETH -> eth += tx.amount
                            CoinAsset.BNB -> bnb += tx.amount
                            CoinAsset.DEMO_COIN -> demoCoin += tx.amount
                            CoinAsset.USDT -> Unit
                        }
                    }
                    "transfer" -> {
                        val asset = CoinAsset.fromSymbol(tx.coin)
                        val isOutgoing = tx.fromUid == currentUid && tx.toUid != currentUid
                        val isIncoming = tx.toUid == currentUid && tx.fromUid != currentUid
                        if (isOutgoing) {
                            when (asset) {
                                CoinAsset.USDT -> usdt += tx.amount
                                CoinAsset.BTC -> btc += tx.amount
                                CoinAsset.ETH -> eth += tx.amount
                                CoinAsset.BNB -> bnb += tx.amount
                                CoinAsset.DEMO_COIN -> demoCoin += tx.amount
                            }
                        } else if (isIncoming) {
                            when (asset) {
                                CoinAsset.USDT -> usdt = (usdt - tx.amount).coerceAtLeast(0.0)
                                CoinAsset.BTC -> btc = (btc - tx.amount).coerceAtLeast(0.0)
                                CoinAsset.ETH -> eth = (eth - tx.amount).coerceAtLeast(0.0)
                                CoinAsset.BNB -> bnb = (bnb - tx.amount).coerceAtLeast(0.0)
                                CoinAsset.DEMO_COIN -> demoCoin = (demoCoin - tx.amount).coerceAtLeast(0.0)
                            }
                        }
                    }
                    "receive" -> {
                        val asset = CoinAsset.fromSymbol(tx.coin)
                        when (asset) {
                            CoinAsset.USDT -> usdt = (usdt - tx.amount).coerceAtLeast(0.0)
                            CoinAsset.BTC -> btc = (btc - tx.amount).coerceAtLeast(0.0)
                            CoinAsset.ETH -> eth = (eth - tx.amount).coerceAtLeast(0.0)
                            CoinAsset.BNB -> bnb = (bnb - tx.amount).coerceAtLeast(0.0)
                            CoinAsset.DEMO_COIN -> demoCoin = (demoCoin - tx.amount).coerceAtLeast(0.0)
                        }
                    }
                }
                txCursor++
            }

            val dayTotal = usdt * priceAtDay(CoinAsset.USDT, dayIndex, livePrices) +
                btc * priceAtDay(CoinAsset.BTC, dayIndex, livePrices) +
                eth * priceAtDay(CoinAsset.ETH, dayIndex, livePrices) +
                bnb * priceAtDay(CoinAsset.BNB, dayIndex, livePrices) +
                demoCoin * priceAtDay(CoinAsset.DEMO_COIN, dayIndex, livePrices)

            val date = Date(dayEndMs)
            val isToday = dayIndex == 6
            pointsMutable.add(
                PortfolioTrendPoint(
                    dayOffsetFromToday = dayIndex - 6,
                    dayLabel = if (isToday) "Today" else dayLabelFormat.format(date),
                    fullDateLabel = if (isToday) "Today" else fullDateFormat.format(date),
                    valueUsdt = dayTotal,
                    isToday = isToday
                )
            )
        }

        val points = pointsMutable.reversed()
        val first = points.firstOrNull()?.valueUsdt ?: 0.0
        val last = points.lastOrNull()?.valueUsdt ?: 0.0
        val changeUsdt = last - first
        val changePercent = when {
            first > 1e-6 -> (changeUsdt / first) * 100.0
            abs(last) > 1e-6 -> 100.0
            else -> 0.0
        }
        val high = points.maxOfOrNull { it.valueUsdt } ?: 0.0
        val low = points.minOfOrNull { it.valueUsdt } ?: 0.0

        return PortfolioTrendSummary(
            points = points,
            changeUsdt = changeUsdt,
            changePercent = changePercent,
            highUsdt = high,
            lowUsdt = low
        )
    }

    private fun priceAtDay(
        asset: CoinAsset,
        dayIndex: Int,
        livePrices: Map<CoinAsset, Double>
    ): Double {
        if (dayIndex == 6) {
            return livePrices[asset] ?: asset.basePriceUsdt
        }
        val multipliers = historicalPriceMultipliers[asset] ?: return asset.basePriceUsdt
        val mult = multipliers.getOrElse(dayIndex) { 1.0 }
        return asset.basePriceUsdt * mult
    }

    private fun buildDayEndMillis(nowMillis: Long): LongArray {
        val result = LongArray(7)
        result[6] = nowMillis
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        for (i in 5 downTo 0) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            result[i] = cal.timeInMillis
        }
        return result
    }
}
