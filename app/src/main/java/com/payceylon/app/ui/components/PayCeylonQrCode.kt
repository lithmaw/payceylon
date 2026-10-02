package com.payceylon.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.payceylon.app.R

private const val FinderSize = 7

/** Encodes [content] with high error correction (so the centre logo can cover modules) and no margin. */
internal fun encodeQr(content: String): BitMatrix = QRCodeWriter().encode(
    content,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    ),
)

/** Module range left blank for the logo: an odd-sized centred square of ~22% of the width. */
internal fun qrLogoZone(moduleCount: Int): IntRange {
    val size = (moduleCount * 0.22f).toInt().let { if (it % 2 == 0) it + 1 else it }
    val start = (moduleCount - size) / 2
    return start until start + size
}

private fun isFinderModule(x: Int, y: Int, n: Int): Boolean =
    (x < FinderSize && y < FinderSize) ||
        (x >= n - FinderSize && y < FinderSize) ||
        (x < FinderSize && y >= n - FinderSize)

/**
 * Renders a PayCeylon payment QR: square data modules, rounded finder "eyes", and the
 * app mark in the centre. Decodes with any standard QR reader, including the in-app scanner.
 */
@Composable
internal fun PayCeylonQrCode(
    content: String,
    modifier: Modifier = Modifier,
    moduleColor: Color = Color.Black,
    backgroundColor: Color = Color.White,
) {
    val matrix = remember(content) { encodeQr(content) }
    val logo = painterResource(R.drawable.app_icon)

    Canvas(modifier = modifier.aspectRatio(1f)) {
        val n = matrix.width
        val cell = size.minDimension / n
        val logoZone = qrLogoZone(n)

        // Data modules; a hairline overlap avoids anti-aliasing seams between neighbours.
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (!matrix[x, y] || isFinderModule(x, y, n)) continue
                if (x in logoZone && y in logoZone) continue
                drawRect(
                    color = moduleColor,
                    topLeft = Offset(x * cell, y * cell),
                    size = Size(cell + 0.6f, cell + 0.6f),
                )
            }
        }

        // Finder patterns: 7x7 rounded ring with a 3x3 rounded centre.
        listOf(Offset.Zero, Offset((n - FinderSize) * cell, 0f), Offset(0f, (n - FinderSize) * cell))
            .forEach { origin ->
                val outer = FinderSize * cell
                drawRoundRect(
                    color = moduleColor,
                    topLeft = origin + Offset(cell / 2f, cell / 2f),
                    size = Size(outer - cell, outer - cell),
                    cornerRadius = CornerRadius(cell * 1.8f),
                    style = Stroke(width = cell),
                )
                drawRoundRect(
                    color = moduleColor,
                    topLeft = origin + Offset(cell * 2f, cell * 2f),
                    size = Size(cell * 3f, cell * 3f),
                    cornerRadius = CornerRadius(cell * 0.9f),
                )
            }

        // Centre logo on a white plate with a one-module quiet border.
        val zoneStart = logoZone.first * cell
        val zoneSize = (logoZone.last - logoZone.first + 1) * cell
        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset(zoneStart, zoneStart),
            size = Size(zoneSize, zoneSize),
            cornerRadius = CornerRadius(cell * 1.5f),
        )
        val inset = cell * 0.8f
        val logoSize = zoneSize - inset * 2
        val logoClip = Path().apply {
            addRoundRect(
                RoundRect(
                    left = zoneStart + inset,
                    top = zoneStart + inset,
                    right = zoneStart + inset + logoSize,
                    bottom = zoneStart + inset + logoSize,
                    cornerRadius = CornerRadius(logoSize * 0.26f),
                ),
            )
        }
        clipPath(logoClip) {
            translate(left = zoneStart + inset, top = zoneStart + inset) {
                with(logo) { draw(Size(logoSize, logoSize)) }
            }
        }
    }
}
