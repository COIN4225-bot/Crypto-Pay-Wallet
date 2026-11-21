package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AlertDirection
import com.example.data.model.CoinAsset
import com.example.data.model.CryptoTransaction
import com.example.data.model.HistoryFilter
import com.example.data.model.MarketCoinTicker
import com.example.data.model.PortfolioTrendCalculator
import com.example.data.model.PortfolioTrendSummary
import com.example.data.model.PriceAlert
import com.example.data.model.TradeActionState
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.Wallet
import com.example.data.repository.CryptoWalletRepository
import com.example.data.repository.MarketPriceEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PriceAlertTriggerEvent(
    val alertId: String,
    val coinSymbol: String,
    val direction: AlertDirection,
    val targetPriceUsdt: Double,
    val currentPriceUsdt: Double
)

class WalletViewModel(
    private val repository: CryptoWalletRepository,
    priceEngine: MarketPriceEngine,
    private val currentUserId: String
) : ViewModel() {

    private val _balanceHidden = MutableStateFlow(false)
    val balanceHidden: StateFlow<Boolean> = _balanceHidden.asStateFlow()

    private val _inrCurrencyMode = MutableStateFlow(false)
    val inrCurrencyMode: StateFlow<Boolean> = _inrCurrencyMode.asStateFlow()

    private val _selectedTradeCoin = MutableStateFlow(CoinAsset.BTC)
    val selectedTradeCoin: StateFlow<CoinAsset> = _selectedTradeCoin.asStateFlow()

    private val _selectedTradeIsBuy = MutableStateFlow(true)
    val selectedTradeIsBuy: StateFlow<Boolean> = _selectedTradeIsBuy.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _actionState = MutableStateFlow<TradeActionState>(TradeActionState.Idle)
    val actionState: StateFlow<TradeActionState> = _actionState.asStateFlow()

    private val _recipientSearchState = MutableStateFlow<UiState<UserProfile?>>(UiState.Success(null))
    val recipientSearchState: StateFlow<UiState<UserProfile?>> = _recipientSearchState.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    private val _alertEvents = MutableSharedFlow<PriceAlertTriggerEvent>(extraBufferCapacity = 16)
    val alertEvents: SharedFlow<PriceAlertTriggerEvent> = _alertEvents.asSharedFlow()

    private val inFlightTriggeredIds = mutableSetOf<String>()

    val marketTickers: StateFlow<List<MarketCoinTicker>> = priceEngine.observeTickers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CoinAsset.entries.map {
                MarketCoinTicker(it, it.basePriceUsdt, if (it == CoinAsset.BNB) -1.14 else 2.45, listOf(it.basePriceUsdt))
            }
        )

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user profile", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load user profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val walletState: StateFlow<UiState<Wallet?>> = repository.observeWallet(currentUserId)
        .map<Wallet?, UiState<Wallet?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing wallet", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load wallet"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val transactionsState: StateFlow<UiState<List<CryptoTransaction>>> = repository.observeTransactions(currentUserId)
        .map<List<CryptoTransaction>, UiState<List<CryptoTransaction>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing transactions", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load transactions"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val priceAlertsState: StateFlow<UiState<List<PriceAlert>>> = repository.observePriceAlerts(currentUserId)
        .map<List<PriceAlert>, UiState<List<PriceAlert>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing price alerts", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load price alerts"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val livePortfolioUsdt: StateFlow<Double> = combine(walletState, marketTickers) { wState, tickers ->
        val wallet = (wState as? UiState.Success)?.data ?: return@combine 0.0
        val prices = tickers.associate { it.asset.symbol to it.priceUsdt }
        wallet.balance +
            wallet.btc * (prices[CoinAsset.BTC.symbol] ?: CoinAsset.BTC.basePriceUsdt) +
            wallet.eth * (prices[CoinAsset.ETH.symbol] ?: CoinAsset.ETH.basePriceUsdt) +
            wallet.bnb * (prices[CoinAsset.BNB.symbol] ?: CoinAsset.BNB.basePriceUsdt) +
            wallet.demoCoin * (prices[CoinAsset.DEMO_COIN.symbol] ?: CoinAsset.DEMO_COIN.basePriceUsdt)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = 0.0
    )

    val portfolioTrend7d: StateFlow<PortfolioTrendSummary> = combine(
        walletState,
        transactionsState,
        marketTickers
    ) { wState, txState, tickers ->
        val wallet = (wState as? UiState.Success)?.data
        val transactions = (txState as? UiState.Success)?.data ?: emptyList()
        PortfolioTrendCalculator.calculate7DayTrend(
            wallet = wallet,
            transactions = transactions,
            tickers = tickers,
            currentUid = currentUserId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PortfolioTrendSummary.EMPTY
    )

    init {
        initializeWalletForCurrentUser()
        monitorPriceThresholds()
    }

    private fun monitorPriceThresholds() {
        viewModelScope.launch {
            combine(marketTickers, priceAlertsState) { tickers, alertsUi ->
                tickers to ((alertsUi as? UiState.Success)?.data ?: emptyList())
            }.collectLatest { (tickers, alerts) ->
                if (alerts.isEmpty()) return@collectLatest
                val priceMap = tickers.associate { it.asset.symbol to it.priceUsdt }

                // Clean up IDs for alerts that have been re-armed or removed
                val activeIds = alerts.filter { it.active }.map { it.id }.toSet()
                inFlightTriggeredIds.retainAll(activeIds)

                alerts.forEach { alert ->
                    if (!alert.active || alert.id.isBlank() || alert.id in inFlightTriggeredIds) {
                        return@forEach
                    }
                    val currentPrice = priceMap[alert.coin] ?: return@forEach
                    val direction = AlertDirection.fromRaw(alert.direction)
                    val crossed = when (direction) {
                        AlertDirection.ABOVE -> currentPrice >= alert.targetPriceUsdt
                        AlertDirection.BELOW -> currentPrice <= alert.targetPriceUsdt
                    }
                    if (crossed) {
                        inFlightTriggeredIds.add(alert.id)
                        _alertEvents.tryEmit(
                            PriceAlertTriggerEvent(
                                alertId = alert.id,
                                coinSymbol = alert.coin,
                                direction = direction,
                                targetPriceUsdt = alert.targetPriceUsdt,
                                currentPriceUsdt = currentPrice
                            )
                        )
                        repository.markPriceAlertTriggered(alert.id, alert.triggeredCount + 1)
                    }
                }
            }
        }
    }

    fun initializeWalletForCurrentUser() {
        val firebaseUser = Firebase.auth.currentUser ?: return
        if (firebaseUser.uid != currentUserId) return
        viewModelScope.launch {
            _initError.value = null
            repository.ensureUserAndWalletInitialized(firebaseUser)
                .onFailure { error ->
                    _initError.value = error.localizedMessage ?: "Failed to initialize wallet."
                }
        }
    }

    fun toggleBalanceVisibility() {
        _balanceHidden.value = !_balanceHidden.value
    }

    fun toggleCurrencyMode() {
        _inrCurrencyMode.value = !_inrCurrencyMode.value
    }

    fun selectTradeCoin(coin: CoinAsset, isBuy: Boolean? = null) {
        _selectedTradeCoin.value = coin
        if (isBuy != null) {
            _selectedTradeIsBuy.value = isBuy
        }
    }

    fun setTradeMode(isBuy: Boolean) {
        _selectedTradeIsBuy.value = isBuy
        if (!isBuy && _selectedTradeCoin.value == CoinAsset.USDT) {
            _selectedTradeCoin.value = CoinAsset.BTC
        }
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun resetActionState() {
        _actionState.value = TradeActionState.Idle
    }

    fun resetRecipientSearch() {
        _recipientSearchState.value = UiState.Success(null)
    }

    fun toggleFavoriteCoin(coinSymbol: String) {
        val currentFavs = (userProfileState.value as? UiState.Success)?.data?.favoriteCoins ?: emptyList()
        val updated = if (coinSymbol in currentFavs) {
            currentFavs - coinSymbol
        } else {
            (currentFavs + coinSymbol).distinct().take(5)
        }
        viewModelScope.launch {
            repository.setFavoriteCoins(updated)
        }
    }

    fun createPriceAlert(coinSymbol: String, direction: AlertDirection, targetPriceUsdt: Double) {
        viewModelScope.launch {
            val currentFavs = (userProfileState.value as? UiState.Success)?.data?.favoriteCoins ?: emptyList()
            if (coinSymbol !in currentFavs) {
                repository.setFavoriteCoins((currentFavs + coinSymbol).distinct().take(5))
            }
            repository.createPriceAlert(coinSymbol, direction, targetPriceUsdt)
                .onSuccess {
                    _actionState.value = TradeActionState.Success("Price alert set for $coinSymbol!")
                }
                .onFailure { error ->
                    _actionState.value = TradeActionState.Error(
                        error.localizedMessage ?: "Failed to create price alert."
                    )
                }
        }
    }

    fun togglePriceAlertActive(alertId: String, active: Boolean) {
        if (active) {
            inFlightTriggeredIds.remove(alertId)
        }
        viewModelScope.launch {
            repository.togglePriceAlertActive(alertId, active)
        }
    }

    fun deletePriceAlert(alertId: String) {
        inFlightTriggeredIds.remove(alertId)
        viewModelScope.launch {
            repository.deletePriceAlert(alertId)
        }
    }

    fun triggerSampleNotification(coinSymbol: String) {
        val ticker = marketTickers.value.firstOrNull { it.asset.symbol == coinSymbol }
        val currentPrice = ticker?.priceUsdt ?: CoinAsset.fromSymbol(coinSymbol).basePriceUsdt
        _alertEvents.tryEmit(
            PriceAlertTriggerEvent(
                alertId = "test_${coinSymbol}_${System.currentTimeMillis()}",
                coinSymbol = coinSymbol,
                direction = AlertDirection.ABOVE,
                targetPriceUsdt = currentPrice * 0.995,
                currentPriceUsdt = currentPrice
            )
        )
    }

    fun buyCrypto(coinSymbol: String, usdtAmount: Double) {
        viewModelScope.launch {
            _actionState.value = TradeActionState.Processing
            val priceMap = marketTickers.value.associate { it.asset.symbol to it.priceUsdt }
            repository.buyCrypto(coinSymbol, usdtAmount, priceMap)
                .onSuccess { tx ->
                    _actionState.value = TradeActionState.Success(
                        message = "Bought ${tx.coin} successfully!",
                        transaction = tx
                    )
                }
                .onFailure { error ->
                    _actionState.value = TradeActionState.Error(
                        error.localizedMessage ?: "Buy transaction failed."
                    )
                }
        }
    }

    fun sellCrypto(coinSymbol: String, coinAmount: Double) {
        viewModelScope.launch {
            _actionState.value = TradeActionState.Processing
            val priceMap = marketTickers.value.associate { it.asset.symbol to it.priceUsdt }
            repository.sellCrypto(coinSymbol, coinAmount, priceMap)
                .onSuccess { tx ->
                    _actionState.value = TradeActionState.Success(
                        message = "Sold ${tx.coin} for Demo USDT!",
                        transaction = tx
                    )
                }
                .onFailure { error ->
                    _actionState.value = TradeActionState.Error(
                        error.localizedMessage ?: "Sell transaction failed."
                    )
                }
        }
    }

    fun lookupRecipient(query: String) {
        viewModelScope.launch {
            _recipientSearchState.value = UiState.Loading
            repository.findUserForTransfer(query)
                .onSuccess { user ->
                    _recipientSearchState.value = UiState.Success(user)
                }
                .onFailure { error ->
                    _recipientSearchState.value = UiState.Error(
                        error.localizedMessage ?: "Recipient not found."
                    )
                }
        }
    }

    fun transferP2P(receiver: UserProfile, coinSymbol: String, amount: Double, note: String?) {
        viewModelScope.launch {
            _actionState.value = TradeActionState.Processing
            val priceMap = marketTickers.value.associate { it.asset.symbol to it.priceUsdt }
            repository.transferP2P(receiver, coinSymbol, amount, priceMap, note)
                .onSuccess { tx ->
                    _actionState.value = TradeActionState.Success(
                        message = "Sent ${tx.coin} to ${receiver.name}!",
                        transaction = tx
                    )
                }
                .onFailure { error ->
                    _actionState.value = TradeActionState.Error(
                        error.localizedMessage ?: "P2P transfer failed."
                    )
                }
        }
    }

    fun claimDemoUsdt(amount: Double = 5000.0) {
        viewModelScope.launch {
            _actionState.value = TradeActionState.Processing
            val priceMap = marketTickers.value.associate { it.asset.symbol to it.priceUsdt }
            repository.claimDemoUsdt(amount, priceMap)
                .onSuccess {
                    _actionState.value = TradeActionState.Success("Credited $5,000 Demo USDT to your wallet!")
                }
                .onFailure { error ->
                    _actionState.value = TradeActionState.Error(
                        error.localizedMessage ?: "Failed to credit Demo USDT."
                    )
                }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "WalletViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
