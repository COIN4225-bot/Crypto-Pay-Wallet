package com.example.data.repository

import com.example.data.model.CoinAsset
import com.example.data.model.MarketCoinTicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt
import kotlin.random.Random

class MarketPriceEngine {

    private val initialPrices = mutableMapOf(
        CoinAsset.BTC to 94250.00,
        CoinAsset.ETH to 3340.50,
        CoinAsset.USDT to 1.00,
        CoinAsset.BNB to 645.20,
        CoinAsset.DEMO_COIN to 12.50
    )

    private val initialChanges = mutableMapOf(
        CoinAsset.BTC to 3.42,
        CoinAsset.ETH to 2.18,
        CoinAsset.USDT to 0.01,
        CoinAsset.BNB to -1.14,
        CoinAsset.DEMO_COIN to 6.85
    )

    private val sparklines = mutableMapOf(
        CoinAsset.BTC to listOf(91200.0, 91850.0, 92400.0, 92100.0, 93050.0, 93800.0, 94250.0),
        CoinAsset.ETH to listOf(3250.0, 3270.0, 3240.0, 3295.0, 3310.0, 3325.0, 3340.5),
        CoinAsset.USDT to listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
        CoinAsset.BNB to listOf(656.0, 654.0, 651.5, 649.0, 650.5, 647.0, 645.2),
        CoinAsset.DEMO_COIN to listOf(11.4, 11.7, 11.6, 12.0, 12.2, 12.35, 12.5)
    )

    fun observeTickers(): Flow<List<MarketCoinTicker>> = flow {
        emit(buildTickers())
        while (true) {
            delay(3500L)
            CoinAsset.entries.forEach { asset ->
                if (asset == CoinAsset.USDT) {
                    initialPrices[asset] = 1.00
                    initialChanges[asset] = 0.01
                } else {
                    val current = initialPrices[asset] ?: asset.basePriceUsdt
                    val pctMove = (Random.nextDouble() - 0.48) * 0.008 // +/- ~0.4%
                    val nextPrice = ((current * (1.0 + pctMove)) * 100.0).roundToInt() / 100.0
                    initialPrices[asset] = nextPrice.coerceAtLeast(0.01)

                    val prevChange = initialChanges[asset] ?: 0.0
                    val nextChange = ((prevChange + pctMove * 100.0) * 100.0).roundToInt() / 100.0
                    initialChanges[asset] = nextChange.coerceIn(-18.0, 25.0)

                    val history = sparklines[asset].orEmpty().drop(1) + nextPrice
                    sparklines[asset] = history
                }
            }
            emit(buildTickers())
        }
    }

    private fun buildTickers(): List<MarketCoinTicker> =
        CoinAsset.entries.map { asset ->
            MarketCoinTicker(
                asset = asset,
                priceUsdt = initialPrices[asset] ?: asset.basePriceUsdt,
                changePercent24h = initialChanges[asset] ?: 0.0,
                sparkline = sparklines[asset] ?: listOf(asset.basePriceUsdt)
            )
        }
}
