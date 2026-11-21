package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AlertDirection
import com.example.data.model.CoinAsset
import com.example.data.model.CryptoTransaction
import com.example.data.model.PriceAlert
import com.example.data.model.UserProfile
import com.example.data.model.Wallet
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class CryptoWalletRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun generateWalletAddress(uid: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }.take(38)
        return "0x$hex"
    }

    suspend fun ensureUserAndWalletInitialized(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        var currentPath = "users/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Signed-in user changed during initialization." }

            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            if (!userSnap.exists()) {
                val displayName = (user.displayName?.takeIf { it.isNotBlank() } ?: "CryptoPay User").take(100)
                val email = (user.email?.takeIf { it.isNotBlank() } ?: "$uid@cryptopay.demo").lowercase().take(200)
                val photoUrl = user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.take(500)
                val userPayload = mapOf(
                    "userId" to uid,
                    "name" to displayName,
                    "email" to email,
                    "walletAddress" to generateWalletAddress(uid),
                    "photoUrl" to photoUrl,
                    "favoriteCoins" to listOf(CoinAsset.BTC.symbol, CoinAsset.ETH.symbol),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                userRef.set(userPayload).await()
            }

            currentPath = "wallets/$uid"
            val walletRef = db.collection("wallets").document(uid)
            val walletSnap = walletRef.get().await()
            if (!walletSnap.exists()) {
                val starterUsdt = 10000.0
                val walletPayload = mapOf(
                    "userId" to uid,
                    "balance" to starterUsdt,
                    "btc" to 0.0,
                    "eth" to 0.0,
                    "bnb" to 0.0,
                    "demoCoin" to 0.0,
                    "totalBalance" to starterUsdt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                walletRef.set(walletPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeWallet(userId: String): Flow<Wallet?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "wallets/$userId"
        emitAll(
            db.collection("wallets").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(Wallet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeTransactions(userId: String): Flow<List<CryptoTransaction>> {
        if (userId.isBlank()) return flowOf(emptyList())
        val sentFlow = db.collection("transactions")
            .whereEqualTo("fromUid", userId)
            .snapshots()
            .map { it.toObjects(CryptoTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val receivedFlow = db.collection("transactions")
            .whereEqualTo("toUid", userId)
            .snapshots()
            .map { it.toObjects(CryptoTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        return combine(sentFlow, receivedFlow) { sent, received ->
            (sent + received)
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp ?: Timestamp(0, 0) }
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                handleFirestoreError(exceptionToLog, OperationType.LIST, "transactions")
            }
            throw error
        }
    }

    suspend fun buyCrypto(
        coinSymbol: String,
        usdtAmount: Double,
        priceMapUsdt: Map<String, Double>
    ): Result<CryptoTransaction> {
        val uid = auth.currentUser?.uid
        val path = "wallets/${uid ?: "unauthenticated"}"
        return try {
            val activeUid = requireUserId()
            val userEmail = auth.currentUser?.email ?: "$activeUid@cryptopay.demo"
            require(usdtAmount > 0.0) { "Enter a valid amount greater than 0." }
            val coin = CoinAsset.fromSymbol(coinSymbol)
            val unitPrice = priceMapUsdt[coin.symbol] ?: coin.basePriceUsdt
            require(unitPrice > 0.0) { "Invalid price quote." }

            val walletRef = db.collection("wallets").document(activeUid)
            val txRef = db.collection("transactions").document()

            val createdTx = db.runTransaction { transaction ->
                val snap = transaction.get(walletRef)
                val wallet = snap.toObject(Wallet::class.java)
                    ?: throw IllegalStateException("Wallet not initialized yet.")

                val boughtQty = if (coin == CoinAsset.USDT) usdtAmount else (usdtAmount / unitPrice)
                var newBalance = wallet.balance
                var newBtc = wallet.btc
                var newEth = wallet.eth
                var newBnb = wallet.bnb
                var newDemoCoin = wallet.demoCoin

                if (coin == CoinAsset.USDT) {
                    newBalance += usdtAmount
                } else {
                    if (wallet.balance < usdtAmount) {
                        throw IllegalArgumentException("Insufficient Demo USDT balance.")
                    }
                    newBalance -= usdtAmount
                    when (coin) {
                        CoinAsset.BTC -> newBtc += boughtQty
                        CoinAsset.ETH -> newEth += boughtQty
                        CoinAsset.BNB -> newBnb += boughtQty
                        CoinAsset.DEMO_COIN -> newDemoCoin += boughtQty
                        CoinAsset.USDT -> Unit
                    }
                }

                val newTotal = computeTotalUsdt(newBalance, newBtc, newEth, newBnb, newDemoCoin, priceMapUsdt)
                transaction.update(
                    walletRef,
                    mapOf(
                        "balance" to newBalance,
                        "btc" to newBtc,
                        "eth" to newEth,
                        "bnb" to newBnb,
                        "demoCoin" to newDemoCoin,
                        "totalBalance" to newTotal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val txPayload = mapOf(
                    "fromUid" to activeUid,
                    "toUid" to activeUid,
                    "fromEmail" to if (coin == CoinAsset.USDT) "CryptoPay Demo Mint" else userEmail,
                    "toEmail" to userEmail,
                    "amount" to boughtQty,
                    "usdtValue" to usdtAmount,
                    "coin" to coin.symbol,
                    "type" to "buy",
                    "status" to "completed",
                    "timestamp" to FieldValue.serverTimestamp()
                )
                transaction.set(txRef, txPayload)

                CryptoTransaction(
                    id = txRef.id,
                    fromUid = activeUid,
                    toUid = activeUid,
                    fromEmail = userEmail,
                    toEmail = userEmail,
                    amount = boughtQty,
                    usdtValue = usdtAmount,
                    coin = coin.symbol,
                    type = "buy",
                    status = "completed",
                    timestamp = Timestamp.now()
                )
            }.await()

            Result.success(createdTx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun sellCrypto(
        coinSymbol: String,
        coinAmount: Double,
        priceMapUsdt: Map<String, Double>
    ): Result<CryptoTransaction> {
        val uid = auth.currentUser?.uid
        val path = "wallets/${uid ?: "unauthenticated"}"
        return try {
            val activeUid = requireUserId()
            val userEmail = auth.currentUser?.email ?: "$activeUid@cryptopay.demo"
            val coin = CoinAsset.fromSymbol(coinSymbol)
            require(coin != CoinAsset.USDT) { "USDT is already your settlement currency." }
            require(coinAmount > 0.0) { "Enter a valid coin quantity greater than 0." }
            val unitPrice = priceMapUsdt[coin.symbol] ?: coin.basePriceUsdt
            val usdtProceeds = coinAmount * unitPrice

            val walletRef = db.collection("wallets").document(activeUid)
            val txRef = db.collection("transactions").document()

            val createdTx = db.runTransaction { transaction ->
                val snap = transaction.get(walletRef)
                val wallet = snap.toObject(Wallet::class.java)
                    ?: throw IllegalStateException("Wallet not initialized yet.")

                val currentHolding = wallet.balanceForCoin(coin.symbol)
                if (currentHolding + 1e-9 < coinAmount) {
                    throw IllegalArgumentException("Insufficient ${coin.symbol} balance.")
                }

                var newBalance = wallet.balance + usdtProceeds
                var newBtc = wallet.btc
                var newEth = wallet.eth
                var newBnb = wallet.bnb
                var newDemoCoin = wallet.demoCoin

                when (coin) {
                    CoinAsset.BTC -> newBtc = (newBtc - coinAmount).coerceAtLeast(0.0)
                    CoinAsset.ETH -> newEth = (newEth - coinAmount).coerceAtLeast(0.0)
                    CoinAsset.BNB -> newBnb = (newBnb - coinAmount).coerceAtLeast(0.0)
                    CoinAsset.DEMO_COIN -> newDemoCoin = (newDemoCoin - coinAmount).coerceAtLeast(0.0)
                    CoinAsset.USDT -> Unit
                }

                val newTotal = computeTotalUsdt(newBalance, newBtc, newEth, newBnb, newDemoCoin, priceMapUsdt)
                transaction.update(
                    walletRef,
                    mapOf(
                        "balance" to newBalance,
                        "btc" to newBtc,
                        "eth" to newEth,
                        "bnb" to newBnb,
                        "demoCoin" to newDemoCoin,
                        "totalBalance" to newTotal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val txPayload = mapOf(
                    "fromUid" to activeUid,
                    "toUid" to activeUid,
                    "fromEmail" to userEmail,
                    "toEmail" to userEmail,
                    "amount" to coinAmount,
                    "usdtValue" to usdtProceeds,
                    "coin" to coin.symbol,
                    "type" to "sell",
                    "status" to "completed",
                    "timestamp" to FieldValue.serverTimestamp()
                )
                transaction.set(txRef, txPayload)

                CryptoTransaction(
                    id = txRef.id,
                    fromUid = activeUid,
                    toUid = activeUid,
                    fromEmail = userEmail,
                    toEmail = userEmail,
                    amount = coinAmount,
                    usdtValue = usdtProceeds,
                    coin = coin.symbol,
                    type = "sell",
                    status = "completed",
                    timestamp = Timestamp.now()
                )
            }.await()

            Result.success(createdTx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun findUserForTransfer(rawQuery: String): Result<UserProfile> {
        val query = rawQuery.trim()
        return try {
            val activeUid = requireUserId()
            require(query.isNotEmpty()) { "Enter an email, wallet address, or UID." }

            var foundUser: UserProfile? = null

            // 1. Check direct UID match if query matches valid Firestore document ID pattern
            if (Regex("^[a-zA-Z0-9_\\-]+$").matches(query) && query.length <= 128) {
                val uidDoc = db.collection("users").document(query).get().await()
                if (uidDoc.exists()) {
                    foundUser = uidDoc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }

            // 2. Check email match (lowercase normalized, plus exact raw fallback)
            if (foundUser == null && query.contains("@")) {
                val emailSnap = db.collection("users")
                    .whereEqualTo("email", query.lowercase())
                    .limit(1)
                    .get()
                    .await()
                foundUser = emailSnap.documents.firstOrNull()
                    ?.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

                if (foundUser == null) {
                    val exactSnap = db.collection("users")
                        .whereEqualTo("email", query)
                        .limit(1)
                        .get()
                        .await()
                    foundUser = exactSnap.documents.firstOrNull()
                        ?.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }

            // 3. Check walletAddress match
            if (foundUser == null) {
                val addrSnap = db.collection("users")
                    .whereEqualTo("walletAddress", query)
                    .limit(1)
                    .get()
                    .await()
                foundUser = addrSnap.documents.firstOrNull()
                    ?.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

            val resolved = foundUser
                ?: throw NoSuchElementException("No CryptoPay user found matching '$query'.")

            if (resolved.userId == activeUid) {
                throw IllegalArgumentException("You cannot transfer funds to your own wallet.")
            }

            Result.success(resolved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users")
            Result.failure(e)
        }
    }

    suspend fun transferP2P(
        receiver: UserProfile,
        coinSymbol: String,
        amount: Double,
        priceMapUsdt: Map<String, Double>,
        note: String?
    ): Result<CryptoTransaction> {
        val uid = auth.currentUser?.uid
        val path = "wallets/${uid ?: "unauthenticated"}"
        return try {
            val activeUid = requireUserId()
            val senderEmail = auth.currentUser?.email ?: "$activeUid@cryptopay.demo"
            require(receiver.userId.isNotBlank() && receiver.userId != activeUid) {
                "Invalid recipient selected."
            }
            require(amount > 0.0) { "Enter a valid transfer amount greater than 0." }
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(140)

            val coin = CoinAsset.fromSymbol(coinSymbol)
            val unitPrice = priceMapUsdt[coin.symbol] ?: coin.basePriceUsdt
            val usdtValue = amount * unitPrice

            val senderWalletRef = db.collection("wallets").document(activeUid)
            val receiverWalletRef = db.collection("wallets").document(receiver.userId)
            val txRef = db.collection("transactions").document()

            val createdTx = db.runTransaction { transaction ->
                // All reads MUST happen before any writes in Firestore transactions
                val senderSnap = transaction.get(senderWalletRef)
                val receiverSnap = transaction.get(receiverWalletRef)

                val senderWallet = senderSnap.toObject(Wallet::class.java)
                    ?: throw IllegalStateException("Your wallet is not ready yet.")
                val receiverWallet = receiverSnap.toObject(Wallet::class.java)
                    ?: throw IllegalStateException("Recipient wallet does not exist yet.")

                val senderCoinHolding = senderWallet.balanceForCoin(coin.symbol)
                if (senderCoinHolding + 1e-9 < amount) {
                    throw IllegalArgumentException("Insufficient ${coin.symbol} balance for transfer.")
                }

                // Deduct from sender
                var sBal = senderWallet.balance
                var sBtc = senderWallet.btc
                var sEth = senderWallet.eth
                var sBnb = senderWallet.bnb
                var sDemo = senderWallet.demoCoin

                when (coin) {
                    CoinAsset.USDT -> sBal = (sBal - amount).coerceAtLeast(0.0)
                    CoinAsset.BTC -> sBtc = (sBtc - amount).coerceAtLeast(0.0)
                    CoinAsset.ETH -> sEth = (sEth - amount).coerceAtLeast(0.0)
                    CoinAsset.BNB -> sBnb = (sBnb - amount).coerceAtLeast(0.0)
                    CoinAsset.DEMO_COIN -> sDemo = (sDemo - amount).coerceAtLeast(0.0)
                }
                val sTotal = computeTotalUsdt(sBal, sBtc, sEth, sBnb, sDemo, priceMapUsdt)

                // Credit to receiver (strictly non-decreasing for security rule verification)
                var rBal = receiverWallet.balance
                var rBtc = receiverWallet.btc
                var rEth = receiverWallet.eth
                var rBnb = receiverWallet.bnb
                var rDemo = receiverWallet.demoCoin

                when (coin) {
                    CoinAsset.USDT -> rBal += amount
                    CoinAsset.BTC -> rBtc += amount
                    CoinAsset.ETH -> rEth += amount
                    CoinAsset.BNB -> rBnb += amount
                    CoinAsset.DEMO_COIN -> rDemo += amount
                }
                val rComputedTotal = computeTotalUsdt(rBal, rBtc, rEth, rBnb, rDemo, priceMapUsdt)
                val rTotal = maxOf(receiverWallet.totalBalance + usdtValue, rComputedTotal)

                transaction.update(
                    senderWalletRef,
                    mapOf(
                        "balance" to sBal,
                        "btc" to sBtc,
                        "eth" to sEth,
                        "bnb" to sBnb,
                        "demoCoin" to sDemo,
                        "totalBalance" to sTotal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                transaction.update(
                    receiverWalletRef,
                    mapOf(
                        "balance" to rBal,
                        "btc" to rBtc,
                        "eth" to rEth,
                        "bnb" to rBnb,
                        "demoCoin" to rDemo,
                        "totalBalance" to rTotal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val txPayload = mapOf(
                    "fromUid" to activeUid,
                    "toUid" to receiver.userId,
                    "fromEmail" to senderEmail,
                    "toEmail" to receiver.email,
                    "amount" to amount,
                    "usdtValue" to usdtValue,
                    "coin" to coin.symbol,
                    "type" to "transfer",
                    "status" to "completed",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "note" to cleanNote
                ).filterValues { it != null } as Map<String, Any>

                transaction.set(txRef, txPayload)

                CryptoTransaction(
                    id = txRef.id,
                    fromUid = activeUid,
                    toUid = receiver.userId,
                    fromEmail = senderEmail,
                    toEmail = receiver.email,
                    amount = amount,
                    usdtValue = usdtValue,
                    coin = coin.symbol,
                    type = "transfer",
                    status = "completed",
                    timestamp = Timestamp.now(),
                    note = cleanNote
                )
            }.await()

            Result.success(createdTx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun claimDemoUsdt(amount: Double = 5000.0, priceMapUsdt: Map<String, Double>): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "wallets/${uid ?: "unauthenticated"}"
        return try {
            val activeUid = requireUserId()
            val userEmail = auth.currentUser?.email ?: "$activeUid@cryptopay.demo"
            val walletRef = db.collection("wallets").document(activeUid)
            val txRef = db.collection("transactions").document()

            db.runTransaction { transaction ->
                val snap = transaction.get(walletRef)
                val wallet = snap.toObject(Wallet::class.java)
                    ?: throw IllegalStateException("Wallet not initialized yet.")
                val newBalance = (wallet.balance + amount).coerceAtMost(1_000_000_000.0)
                val newTotal = computeTotalUsdt(
                    newBalance,
                    wallet.btc,
                    wallet.eth,
                    wallet.bnb,
                    wallet.demoCoin,
                    priceMapUsdt
                )
                transaction.update(
                    walletRef,
                    mapOf(
                        "balance" to newBalance,
                        "totalBalance" to newTotal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.set(
                    txRef,
                    mapOf(
                        "fromUid" to activeUid,
                        "toUid" to activeUid,
                        "fromEmail" to "CryptoPay Faucet",
                        "toEmail" to userEmail,
                        "amount" to amount,
                        "usdtValue" to amount,
                        "coin" to CoinAsset.USDT.symbol,
                        "type" to "receive",
                        "status" to "completed",
                        "timestamp" to FieldValue.serverTimestamp(),
                        "note" to "Demo USDT Faucet Top-Up"
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observePriceAlerts(userId: String): Flow<List<PriceAlert>> {
        if (userId.isBlank()) return flowOf(emptyList())
        val path = "users/$userId/priceAlerts"
        return db.collection("users").document(userId).collection("priceAlerts")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PriceAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun setFavoriteCoins(favorites: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val activeUid = requireUserId()
            val bounded = favorites.distinct().take(5)
            db.collection("users").document(activeUid)
                .update(
                    mapOf(
                        "favoriteCoins" to bounded,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createPriceAlert(
        coinSymbol: String,
        direction: AlertDirection,
        targetPriceUsdt: Double
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/priceAlerts"
        return try {
            val activeUid = requireUserId()
            require(targetPriceUsdt > 0.0 && targetPriceUsdt <= 100_000_000.0) {
                "Target price must be between $0.01 and $100,000,000."
            }
            val coin = CoinAsset.fromSymbol(coinSymbol)
            val alertRef = db.collection("users").document(activeUid)
                .collection("priceAlerts").document()
            val payload = mapOf(
                "userId" to activeUid,
                "coin" to coin.symbol,
                "direction" to direction.name,
                "targetPriceUsdt" to targetPriceUsdt,
                "active" to true,
                "triggeredCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            alertRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun togglePriceAlertActive(alertId: String, active: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/priceAlerts/$alertId"
        return try {
            val activeUid = requireUserId()
            require(alertId.isNotBlank()) { "Alert ID is required." }
            db.collection("users").document(activeUid)
                .collection("priceAlerts").document(alertId)
                .update(
                    mapOf(
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markPriceAlertTriggered(alertId: String, nextTriggeredCount: Long): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/priceAlerts/$alertId"
        return try {
            val activeUid = requireUserId()
            require(alertId.isNotBlank()) { "Alert ID is required." }
            db.collection("users").document(activeUid)
                .collection("priceAlerts").document(alertId)
                .update(
                    mapOf(
                        "active" to false,
                        "triggeredCount" to nextTriggeredCount,
                        "lastTriggeredAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePriceAlert(alertId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}/priceAlerts/$alertId"
        return try {
            val activeUid = requireUserId()
            require(alertId.isNotBlank()) { "Alert ID is required." }
            db.collection("users").document(activeUid)
                .collection("priceAlerts").document(alertId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun computeTotalUsdt(
        balance: Double,
        btc: Double,
        eth: Double,
        bnb: Double,
        demoCoin: Double,
        prices: Map<String, Double>
    ): Double {
        val btcPrice = prices[CoinAsset.BTC.symbol] ?: CoinAsset.BTC.basePriceUsdt
        val ethPrice = prices[CoinAsset.ETH.symbol] ?: CoinAsset.ETH.basePriceUsdt
        val bnbPrice = prices[CoinAsset.BNB.symbol] ?: CoinAsset.BNB.basePriceUsdt
        val demoPrice = prices[CoinAsset.DEMO_COIN.symbol] ?: CoinAsset.DEMO_COIN.basePriceUsdt
        return balance + (btc * btcPrice) + (eth * ethPrice) + (bnb * bnbPrice) + (demoCoin * demoPrice)
    }
}
