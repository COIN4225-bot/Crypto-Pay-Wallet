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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.data.model.HistoryFilter
import com.example.data.model.UiState
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.BinanceGold
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.CryptoRedSoft
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.WalletViewModel

@Composable
fun HistoryScreen(
    viewModel: WalletViewModel,
    currentUid: String
) {
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val activeFilter by viewModel.historyFilter.collectAsStateWithLifecycle()

    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()
    val filteredTransactions = allTransactions.filter { tx ->
        when (activeFilter) {
            HistoryFilter.ALL -> true
            HistoryFilter.BUY -> tx.type == "buy"
            HistoryFilter.SELL -> tx.type == "sell"
            HistoryFilter.SEND -> tx.type == "transfer" && tx.fromUid == currentUid
            HistoryFilter.RECEIVE -> (tx.type == "transfer" && tx.toUid == currentUid) || tx.type == "receive"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text(
                text = "Transaction History",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilter.entries.forEach { filter ->
                    val selected = filter == activeFilter
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { viewModel.setHistoryFilter(filter) }
                            .testTag("history_filter_${filter.name.lowercase()}"),
                        color = if (selected) BinanceGold else CardSurface,
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, if (selected) BinanceGold else CardBorder)
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (selected) ObsidianDeep else TextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        when (transactionsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BinanceGold)
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        color = CryptoRedSoft,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = (transactionsState as UiState.Error).message,
                            style = MaterialTheme.typography.bodyLarge.copy(color = CryptoRed),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.ReceiptLong,
                                contentDescription = "No matching transactions",
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No ${activeFilter.label.lowercase()} transactions",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Trades and P2P transfers will appear here immediately.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTransactions, key = { it.id }) { tx ->
                            TransactionItemCard(transaction = tx, currentUid = currentUid)
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}
