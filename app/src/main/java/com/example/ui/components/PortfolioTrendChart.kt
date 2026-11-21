package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.CryptoFormatters
import com.example.data.model.PortfolioTrendSummary
import com.example.ui.theme.BinanceGold
import com.example.ui.theme.BinanceGoldSoft
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardElevated
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoGreenSoft
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.CryptoRedSoft
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PortfolioTrendChartCard(
    trend: PortfolioTrendSummary,
    isLoading: Boolean,
    balanceHidden: Boolean,
    inrMode: Boolean,
    modifier: Modifier = Modifier
) {
    val points = trend.points
    var selectedIndex by remember(points.size) { mutableStateOf<Int?>(null) }
    val activePoint = selectedIndex?.let { points.getOrNull(it) }

    val isPositive = trend.changeUsdt >= 0.0
    val trendColor = when {
        abs(trend.changeUsdt) < 0.01 -> BinanceGold
        isPositive -> CryptoGreen
        else -> CryptoRed
    }
    val pillBg = when {
        abs(trend.changeUsdt) < 0.01 -> BinanceGoldSoft
        isPositive -> CryptoGreenSoft
        else -> CryptoRedSoft
    }

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points.isNotEmpty()) {
        if (points.isNotEmpty()) {
            reveal.snapTo(0f)
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("portfolio_7d_trend_card"),
        color = CardSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPositive) {
                                Icons.AutoMirrored.Filled.TrendingUp
                            } else {
                                Icons.AutoMirrored.Filled.TrendingDown
                            },
                            contentDescription = "7-Day Portfolio Trend",
                            tint = trendColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "7-Day Portfolio Trend",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val subtitleText = when {
                        isLoading || points.isEmpty() -> "Calculating 7-day valuation..."
                        activePoint != null && balanceHidden -> "${activePoint.fullDateLabel} • ••••••"
                        activePoint != null -> "${activePoint.fullDateLabel} • ${
                            CryptoFormatters.formatPrice(activePoint.valueUsdt, inrMode)
                        }"
                        else -> "Tap or drag chart to inspect daily close"
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (activePoint != null) BinanceGold else TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = pillBg,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    val sign = if (trend.changeUsdt > 0.005) "+" else if (trend.changeUsdt < -0.005) "-" else ""
                    val deltaText = if (balanceHidden) {
                        "••••"
                    } else {
                        "$sign${String.format(Locale.US, "%.2f", abs(trend.changePercent))}%"
                    }
                    Text(
                        text = "7D $deltaText",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = trendColor,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoading || points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading portfolio history...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                val values = points.map { it.valueUsdt }
                val rawMin = values.minOrNull() ?: 0.0
                val rawMax = values.maxOrNull() ?: 0.0
                val isFlat = abs(rawMax - rawMin) < 1e-6
                val paddingSpan = if (isFlat) (rawMax.coerceAtLeast(100.0) * 0.05) else (rawMax - rawMin) * 0.18
                val plotMin = (rawMin - paddingSpan).coerceAtLeast(0.0)
                val plotMax = rawMax + paddingSpan
                val plotRange = (plotMax - plotMin).takeIf { it > 1e-9 } ?: 1.0

                val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            .testTag("portfolio_7d_trend_canvas")
                            .pointerInput(points) {
                                detectTapGestures { offset ->
                                    if (points.size > 1 && size.width > 0) {
                                        val stepX = size.width.toFloat() / (points.size - 1)
                                        val tapped = (offset.x / stepX).roundToInt()
                                            .coerceIn(0, points.lastIndex)
                                        selectedIndex = if (selectedIndex == tapped) null else tapped
                                    }
                                }
                            }
                            .pointerInput(points) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        if (points.size > 1 && size.width > 0) {
                                            val stepX = size.width.toFloat() / (points.size - 1)
                                            selectedIndex = (offset.x / stepX).roundToInt()
                                                .coerceIn(0, points.lastIndex)
                                        }
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        if (points.size > 1 && size.width > 0) {
                                            val stepX = size.width.toFloat() / (points.size - 1)
                                            selectedIndex = (change.position.x / stepX).roundToInt()
                                                .coerceIn(0, points.lastIndex)
                                        }
                                    },
                                    onDragEnd = { selectedIndex = null },
                                    onDragCancel = { selectedIndex = null }
                                )
                            }
                    ) {
                        val chartW = size.width
                        val chartH = size.height
                        if (chartW <= 0f || chartH <= 0f) return@Canvas

                        // Horizontal dashed gridlines (top, mid, bottom)
                        listOf(0.12f, 0.50f, 0.88f).forEach { fraction ->
                            val gridY = chartH * fraction
                            drawLine(
                                color = CardBorder.copy(alpha = 0.7f),
                                start = Offset(0f, gridY),
                                end = Offset(chartW, gridY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }

                        val stepX = if (points.size > 1) chartW / (points.size - 1) else chartW
                        val coords = points.mapIndexed { idx, pt ->
                            val x = idx * stepX
                            val normY = ((pt.valueUsdt - plotMin) / plotRange).toFloat().coerceIn(0f, 1f)
                            val y = chartH - (normY * (chartH * 0.76f) + chartH * 0.12f)
                            Offset(x, y)
                        }

                        val linePath = Path().apply {
                            coords.forEachIndexed { index, pt ->
                                if (index == 0) {
                                    moveTo(pt.x, pt.y)
                                } else {
                                    val prev = coords[index - 1]
                                    val midX = (prev.x + pt.x) / 2f
                                    cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                                }
                            }
                        }

                        val areaPath = Path().apply {
                            addPath(linePath)
                            val last = coords.last()
                            val first = coords.first()
                            lineTo(last.x, chartH)
                            lineTo(first.x, chartH)
                            close()
                        }

                        clipRect(right = chartW * reveal.value) {
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        trendColor.copy(alpha = 0.28f),
                                        trendColor.copy(alpha = 0.04f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = chartH
                                )
                            )

                            drawPath(
                                path = linePath,
                                color = trendColor,
                                style = Stroke(
                                    width = 2.6.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Subtle node dots across the 7 days
                            coords.forEachIndexed { index, center ->
                                val isHighlighted = index == (selectedIndex ?: coords.lastIndex)
                                if (!isHighlighted) {
                                    drawCircle(
                                        color = ObsidianDeep,
                                        radius = 3.5.dp.toPx(),
                                        center = center
                                    )
                                    drawCircle(
                                        color = trendColor.copy(alpha = 0.85f),
                                        radius = 2.5.dp.toPx(),
                                        center = center
                                    )
                                }
                            }
                        }

                        // Highlight crosshair + active node marker
                        val highlightIdx = selectedIndex ?: coords.lastIndex
                        val activeCoord = coords.getOrNull(highlightIdx)
                        if (activeCoord != null && reveal.value > 0.9f) {
                            if (selectedIndex != null) {
                                drawLine(
                                    color = BinanceGold.copy(alpha = 0.65f),
                                    start = Offset(activeCoord.x, 0f),
                                    end = Offset(activeCoord.x, chartH),
                                    strokeWidth = 1.2.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                            }
                            val markerColor = if (selectedIndex != null) BinanceGold else trendColor
                            drawCircle(
                                color = markerColor.copy(alpha = 0.24f),
                                radius = 9.dp.toPx(),
                                center = activeCoord
                            )
                            drawCircle(
                                color = ObsidianDeep,
                                radius = 5.5.dp.toPx(),
                                center = activeCoord
                            )
                            drawCircle(
                                color = markerColor,
                                radius = 4.dp.toPx(),
                                center = activeCoord
                            )
                        }
                    }
                }

                // X-Axis Day Labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    points.forEachIndexed { idx, pt ->
                        val isHighlighted = idx == (selectedIndex ?: points.lastIndex)
                        Text(
                            text = pt.dayLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = when {
                                selectedIndex == idx -> BinanceGold
                                pt.isToday -> TextPrimary
                                else -> TextTertiary
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 7-Day Min / Max / Net Change summary bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardElevated)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrendStatColumn(
                        label = "7D Low",
                        value = if (balanceHidden) "••••" else CryptoFormatters.formatPrice(trend.lowUsdt, inrMode),
                        valueColor = TextPrimary
                    )
                    TrendStatColumn(
                        label = "7D High",
                        value = if (balanceHidden) "••••" else CryptoFormatters.formatPrice(trend.highUsdt, inrMode),
                        valueColor = TextPrimary
                    )
                    TrendStatColumn(
                        label = "7D Net",
                        value = if (balanceHidden) {
                            "••••"
                        } else {
                            val prefix = if (trend.changeUsdt > 0.005) "+" else if (trend.changeUsdt < -0.005) "-" else ""
                            "$prefix${CryptoFormatters.formatPrice(abs(trend.changeUsdt), inrMode)}"
                        },
                        valueColor = trendColor,
                        horizontalAlignment = Alignment.End
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendStatColumn(
    label: String,
    value: String,
    valueColor: Color,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                color = valueColor,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
