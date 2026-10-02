package com.payceylon.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary

/**
 * Labelled single-line input on a card surface. The border turns PayCeylon red while
 * focused; [error] shows underneath in the price-down colour.
 */
@Composable
internal fun PayCeylonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    helper: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    prefix: String? = null,
    enabled: Boolean = true,
) {
    val colors = LocalWalletColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        error != null -> colors.priceDown
        isFocused -> colors.content
        else -> colors.card
    }
    // Tracks the cursor so prefilled values start with it at the end; external changes
    // (filtering, formatting) also move it to the end.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val current = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))
    val shape = RoundedCornerShape(18.dp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = colors.contentSecondary,
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        BasicTextField(
            value = current,
            onValueChange = {
                fieldValue = it
                if (it.text != value) onValueChange(it.text)
            },
            enabled = enabled,
            singleLine = true,
            interactionSource = interactionSource,
            textStyle = TextStyle(
                color = colors.content,
                fontFamily = InterFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(PayCeylonPrimary),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                imeAction = imeAction,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(shape)
                        .background(colors.card)
                        .border(1.5.dp, borderColor, shape)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (prefix != null) {
                        Text(
                            text = prefix,
                            color = colors.contentSecondary,
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                color = colors.contentTertiary,
                                fontFamily = InterFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
        val message = error ?: helper
        if (message != null) {
            Text(
                text = message,
                color = if (error != null) colors.priceDown else colors.contentSecondary,
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
