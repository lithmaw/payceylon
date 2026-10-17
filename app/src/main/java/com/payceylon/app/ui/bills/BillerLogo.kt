package com.payceylon.app.ui.bills

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.payceylon.app.R
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.wallet.MethodBadge

/** The company's logo for each biller id; Mobitel and SLT Fibre are both SLT-Mobitel. */
@DrawableRes
internal fun billerLogo(billerId: String): Int? = when (billerId) {
    "ceb" -> R.drawable.logo_ceb
    "leco" -> R.drawable.logo_leco
    "nwsdb" -> R.drawable.logo_nwsdb
    "dialog" -> R.drawable.logo_dialog
    "mobitel", "slt" -> R.drawable.logo_sltmobitel
    "hutch" -> R.drawable.logo_hutch
    "dialogtv" -> R.drawable.logo_dialogtv
    else -> null
}

/** The logo for a bill transaction, whose counterparty is the biller's short name. */
@DrawableRes
internal fun billerLogoForName(shortName: String): Int? = when (shortName) {
    "CEB" -> "ceb"
    "LECO" -> "leco"
    "Water Board" -> "nwsdb"
    "Dialog" -> "dialog"
    "Mobitel" -> "mobitel"
    "Hutch" -> "hutch"
    "SLT Fibre" -> "slt"
    "Dialog TV" -> "dialogtv"
    else -> null
}?.let(::billerLogo)

/** The biller's logo, or its category icon for a biller without one. */
@Composable
internal fun BillerBadge(biller: Biller, size: Dp = 40.dp) {
    val logo = billerLogo(biller.id)
    if (logo != null) BillerLogo(logo, size) else MethodBadge(biller.category.icon, size.value.toInt())
}

/** A company logo on a white rounded square (not a circle, so wide wordmarks keep their size). */
@Composable
internal fun BillerLogo(@DrawableRes logo: Int, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, LocalWalletColors.current.subtle, shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painter = painterResource(logo), contentDescription = null, modifier = Modifier.size(size * 0.94f))
    }
}