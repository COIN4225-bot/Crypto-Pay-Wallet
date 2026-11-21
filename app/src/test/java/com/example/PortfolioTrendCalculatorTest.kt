package com.example

import com.example.data.model.CoinAsset
import com.example.data.model.CryptoTransaction
import com.example.data.model.MarketCoinTicker
import com.example.data.model.PortfolioTrendCalculator
import com.example.data.model.Wallet
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PortfolioTrendCalculatorTest {

    private val defaultTickers = CoinAsset.entries.map {
        MarketCoinTicker(it, it.basePriceUsdt, 1.5, listOf(it.basePriceUsdt))
    }

    @Test
    fun calculates_seven_points_with_live_btc_appreciation() {
        val wallet = Wallet(
            id = "user1",
            userId = "user1",
            balance = 5000.0,
            btc = 0.1
        )
        val now = 1_763_720_000_000L
        val summary = PortfolioTrendCalculator.calculate7DayTrend(
            wallet = wallet,
            transactions = emptyList(),
            tickers = defaultTickers,
            currentUid = "user1",
            nowMillis = now
        )

        assertEquals(7, summary.points.size)
        assertTrue(summary.points.last().isToday)
        assertEquals("Today", summary.points.last().dayLabel)
        // Today = 5000 + 0.1 * 94250 = 14425.0
        assertEquals(14425.0, summary.points.last().valueUsdt, 0.01)
        // 6 days ago BTC multiplier was 0.952, so portfolio value was lower
        assertTrue(summary.changeUsdt > 0.0)
        assertTrue(summary.changePercent > 0.0)
    }

    @Test
    fun reconstructs_faucet_receive_from_today_backward() {
        val now = 1_763_720_000_000L
        val wallet = Wallet(
            id = "user1",
            userId = "user1",
            balance = 15000.0
        )
        val faucetTx = CryptoTransaction(
            id = "tx1",
            fromUid = "system",
            toUid = "user1",
            amount = 5000.0,
            usdtValue = 5000.0,
            coin = "USDT",
            type = "receive",
            status = "completed",
            timestamp = Timestamp(Date(now - 60_000L))
        )

        val summary = PortfolioTrendCalculator.calculate7DayTrend(
            wallet = wallet,
            transactions = listOf(faucetTx),
            tickers = defaultTickers,
            currentUid = "user1",
            nowMillis = now
        )

        assertEquals(7, summary.points.size)
        // Before today's 5000 USDT claim, historical days had 10000 USDT
        assertEquals(10000.0, summary.points.first().valueUsdt, 0.01)
        assertEquals(15000.0, summary.points.last().valueUsdt, 0.01)
        assertEquals(5000.0, summary.changeUsdt, 0.01)
        assertEquals(50.0, summary.changePercent, 0.01)
    }
}
