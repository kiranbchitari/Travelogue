package com.travelogue.app.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.components.IconBadge
import com.travelogue.app.core.designsystem.components.colorValue
import com.travelogue.app.core.designsystem.components.icon
import com.travelogue.app.core.designsystem.components.pressable
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.ExpenseCategoryEntity
import com.travelogue.app.data.db.entity.ExpenseEntity
import java.time.LocalDate

/**
 * One expense line: category badge, title, meta line and amount. Sits directly on the page
 * background (no card) so long lists stay calm.
 */
@Composable
fun ExpenseRow(
    expense: ExpenseEntity,
    category: ExpenseCategoryEntity?,
    meta: String,
    convertedLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overline: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick)
            .padding(horizontal = Space.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(category.icon(), category.colorValue(), size = 44.dp, iconSize = 20.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(overline, style = Travel.type.bodySmall, color = Travel.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(expense.title, style = Travel.type.titleSmall, color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) {
                Text(meta, style = Travel.type.bodySmall, color = Travel.colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Space.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(expense.amount, expense.currency), style = Travel.type.numberSmall.copy(fontSize = Travel.type.titleSmall.fontSize), color = Travel.colors.ink)
            if (convertedLabel != null) Text(convertedLabel, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
    }
}

/** Day heading for grouped expense lists: weekday + date on the left, day total on the right. */
@Composable
fun DayHeader(date: LocalDate, total: String?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Space.gutter, end = Space.gutter, top = Space.lg, bottom = Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            Fmt.weekday(date),
            style = Travel.type.label,
            color = Travel.colors.inkSoft,
            modifier = Modifier.weight(1f),
        )
        if (total != null) Text(total, style = Travel.type.label, color = Travel.colors.inkFaint)
    }
}

/** Meta line: "2:30 PM · Colva Beach · Paid by Asha". */
fun expenseMeta(expense: ExpenseEntity, payerName: String?, extra: String? = null): String =
    listOfNotNull(
        extra,
        expense.time?.let(Fmt::time),
        expense.location.takeIf { it.isNotBlank() },
        payerName?.let { "Paid by $it" },
    ).joinToString(" · ")

/** Shared card padding for summary sections on these screens. */
val SummaryPadding = PaddingValues(Space.xl)


/** Locale-independent decimal for pre-filling inputs ("1250.5"), matching toAmountOrNull parsing. */
fun plainNumber(v: Double, decimals: Int = 2): String =
    java.math.BigDecimal.valueOf(v).setScale(decimals, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
