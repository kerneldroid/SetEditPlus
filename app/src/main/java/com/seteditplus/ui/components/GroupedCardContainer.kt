package com.seteditplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grouped card container with M3 Expressive rounded corners.
 * Items inside should use [groupedItemShape] for conditional corner rounding.
 */
@Composable
fun GroupedCardContainer(
    modifier: Modifier = Modifier,
    spacing: Dp = 2.dp,
    cornerRadius: Dp = 28.dp,
    containerColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/**
 * Returns the appropriate shape for an item in a grouped card list.
 *
 * @param isFirst Whether this is the first item in the group.
 * @param isLast Whether this is the last item in the group.
 * @param cornerRadius Outer corner radius (expressive, large).
 * @param connectionRadius Inner joint radius (never 0.dp).
 */
fun groupedItemShape(
    isFirst: Boolean,
    isLast: Boolean,
    cornerRadius: Dp = 28.dp,
    connectionRadius: Dp = 4.dp
): RoundedCornerShape {
    return when {
        isFirst && isLast -> RoundedCornerShape(cornerRadius)
        isFirst -> RoundedCornerShape(
            topStart = cornerRadius,
            topEnd = cornerRadius,
            bottomStart = connectionRadius,
            bottomEnd = connectionRadius
        )
        isLast -> RoundedCornerShape(
            topStart = connectionRadius,
            topEnd = connectionRadius,
            bottomStart = cornerRadius,
            bottomEnd = cornerRadius
        )
        else -> RoundedCornerShape(connectionRadius)
    }
}
