package com.payceylon.app.ui.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.abs

/**
 * One card per token for the crypto wallet: what the holding is worth in LKR, the 24 h price
 * change and a price chart. Scrolls sideways, edge to edge past the page's [pagePadding].
 */
@Composable
internal fun TokenCardRow(
    balanceOf: (Asset) -> BigDecimal,
    hidden: Boolean,
    onTokenClick: (CryptoToken) -> Unit,
    pagePadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.bleed(pagePadding),
        contentPadding = PaddingValues(horizontal = pagePadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(CryptoToken.entries, key = { it.symbol }) { token ->
            TokenCard(
                token = token,
                holding = balanceOf(Asset.of(token)),
                hidden = hidden,
                onClick = { onTokenClick(token) },
            )
        }
    }
}

/** Lets a child of a padded list reach the screen edges. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun TokenCard(token: CryptoToken, holding: BigDecimal, hidden: Boolean, onClick: () -> Unit) {
    val colors = LocalWalletColors.current
    val history = remember(token) { MarketHistory.of(token) }
    val isDown = history.changePercent < 0
    val trend = if (isDown) colors.priceDown else colors.priceUp
    // Styled like the transaction rows below: a plain card surface.
    Column(
        modifier = Modifier
            .width(196.dp)
            .bounceClick(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = token.symbol,
                        color = colors.content,
                        fontFamily = InterFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = token.tokenName,
                        color = colors.contentTertiary,
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.subtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painter = painterResource(token.icon), contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            val value = MarketHistory.valueInLkr(token, holding)
            val (whole, fraction) = value.toMoneyParts(2)
            BasicText(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.contentTertiary, fontSize = 14.sp)) { append("LKR ") }
                    if (hidden) {
                        append("\u2022\u2022\u2022\u2022\u2022")
                    } else {
                        append(whole)
                        withStyle(SpanStyle(color = colors.contentTertiary)) { append(fraction) }
                    }
                },
                style = TextStyle(
                    color = colors.content,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.6).sp,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 24.sp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hidden) "\u2022\u2022\u2022\u2022 ${token.symbol}" else "${holding.toAmountString(Asset.of(token))} ${token.symbol}",
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${if (isDown) "\u2193" else "\u2191"} ${String.format(Locale.US, "%.2f", abs(history.changePercent))}%  ",
                color = trend,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Sparkline(
            prices = history.prices,
            color = trend,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        )
    }
}

/** Price line with a fading fill under it, edge to edge. */
@Composable
private fun Sparkline(prices: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val min = prices.min()
        val max = prices.max()
        // Stablecoins barely move; a minimum span keeps their line flat instead of jagged.
        val span = maxOf(max - min, prices.last() * 0.01)
        val mid = (max + min) / 2
        val top = 8.dp.toPx()
        val usable = size.height - top - 6.dp.toPx()
        fun point(i: Int) = Offset(
            x = size.width * i / (prices.size - 1),
            y = top + usable * (0.5f - ((prices[i] - mid) / span).toFloat()),
        )
        val line = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until prices.size) {
                // Smooth the walk with midpoints so the line reads as a curve.
                val prev = point(i - 1)
                val cur = point(i)
                quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2, (prev.y + cur.y) / 2)
            }
            lineTo(point(prices.size - 1).x, point(prices.size - 1).y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}