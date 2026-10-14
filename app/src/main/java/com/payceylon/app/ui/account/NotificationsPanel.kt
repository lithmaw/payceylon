package com.payceylon.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.R
import com.payceylon.app.ui.components.DropdownAnchor
import com.payceylon.app.ui.components.PayCeylonPopup
import com.payceylon.app.ui.components.tap
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.payceylon.app.ui.wallet.TransactionGlyph
import com.payceylon.app.ui.wallet.WalletTransaction

private enum class NotificationFilter(val label: String) { All("All"), Transactions("Transactions"), Account("Account") }

/**
 * Dropdown feed under the Home bell: transactions and account / security changes, newest
 * first. Place it in the same Box as the bell.
 */
@Composable
internal fun NotificationsPanel(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    notifications: List<AppNotification>,
    transactionFor: (String) -> WalletTransaction?,
    onMarkAllRead: () -> Unit,
    onNotificationClick: (AppNotification) -> Unit,
) {
    val colors = LocalWalletColors.current
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val filter = NotificationFilter.entries[filterIndex]
    val shown = notifications.filter {
        when (filter) {
            NotificationFilter.All -> true
            NotificationFilter.Transactions -> it.kind == NotificationKind.Transaction
            NotificationFilter.Account -> it.kind != NotificationKind.Transaction
        }
    }
    val hasUnread = notifications.any { !it.read }

    PayCeylonPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        anchor = DropdownAnchor.End,
        width = 340.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Notifications",
                modifier = Modifier.weight(1f),
                color = colors.content,
                fontFamily = InterFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (hasUnread) {
                Text(
                    text = "Mark all read",
                    modifier = Modifier.tap(onClick = onMarkAllRead),
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NotificationFilter.entries.forEachIndexed { index, option ->
                val selected = index == filterIndex
                Text(
                    text = option.label,
                    modifier = Modifier
                        .tap { filterIndex = index }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colors.content else colors.subtle)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (selected) colors.page else colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(
            modifier = Modifier
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (shown.isEmpty()) {
                Text(
                    text = "You're all caught up.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            shown.forEach { notification ->
                NotificationRow(
                    notification = notification,
                    transaction = notification.transactionId?.let(transactionFor),
                    onClick = { onNotificationClick(notification) },
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, transaction: WalletTransaction?, onClick: () -> Unit) {
    val colors = LocalWalletColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (notification.read) Color.Transparent else colors.subtle)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(if (notification.read) colors.subtle else colors.card, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                transaction != null -> TransactionGlyph(transaction.direction, size = 20.dp)
                else -> LineIcon(if (notification.kind == NotificationKind.Security) R.drawable.ic_shield else R.drawable.ic_user)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.title,
                    modifier = Modifier.weight(1f),
                    color = colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = if (notification.read) FontWeight.Medium else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = relativeTime(notification.timestamp),
                    modifier = Modifier.padding(start = 8.dp),
                    color = colors.contentTertiary,
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = notification.body,
                color = colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!notification.read) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(7.dp)
                    .background(PayCeylonPrimary, CircleShape),
            )
        }
    }
}
