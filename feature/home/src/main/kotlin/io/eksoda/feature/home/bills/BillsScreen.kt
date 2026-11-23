package io.eksoda.feature.home.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.HeroNumberStyle
import io.eksoda.core.designsystem.theme.PillShape
import io.eksoda.core.model.BillKind
import io.eksoda.core.model.BillLine
import io.eksoda.core.model.BillsMonth
import io.eksoda.core.model.Money
import io.eksoda.core.ui.component.AmountDialog
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.monthName
import io.eksoda.feature.home.MonthTabs
import io.eksoda.feature.home.R

@Composable
fun BillsRoute(onBack: () -> Unit, viewModel: BillsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val failed = stringResource(R.string.bills_save_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BillsEffect.SaveFailed -> snackbarHostState.showSnackbar(failed)
            }
        }
    }
    BillsScreen(state, viewModel::onEvent, onBack, snackbarHostState)
}

/**
 * The bills, kept apart from receipts (docs/design/ui-redesign.md §5): month tabs, what the month
 * comes to, and each bill. In the current month an amount can be entered or changed; a past
 * month shows its amounts in gray with a lock and takes no taps. "+" adds a bill, and a bill's
 * name opens it to edit or stop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    state: BillsUiState,
    onEvent: (BillsEvent) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var amountOf by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bills_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.bills_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { adding = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.bills_add),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val month = state.month
        if (state.loading || month == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                DelayedProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            MonthTabs(state.months, month.month, emptySet()) {
                onEvent(BillsEvent.MonthPicked(it))
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TotalCard(month)
                if (month.lines.isEmpty()) {
                    Text(
                        stringResource(R.string.bills_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = EksodaTheme.colors.ink2,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    SectionCard {
                        month.lines.forEachIndexed { index, line ->
                            if (index > 0) HorizontalDivider(color = EksodaTheme.colors.line)
                            BillRow(
                                line = line,
                                currency = month.currency,
                                monthName = monthName(month.month, appLocale()),
                                onNameClick = { editing = line.bill.id },
                                onAmountClick = { amountOf = line.bill.id },
                            )
                        }
                    }
                }
                Text(
                    stringResource(
                        if (month.current) {
                            R.string.bills_note_current
                        } else {
                            R.string.bills_note_past
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = EksodaTheme.colors.ink2,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        amountOf?.let { id -> month.lines.firstOrNull { it.bill.id == id } }?.let { line ->
            AmountEditor(line, month, onEvent, onClose = { amountOf = null })
        }
    }
    if (adding) {
        BillSheet(
            initial = null,
            onSave = { draft ->
                adding = false
                onEvent(BillsEvent.Add(draft))
            },
            onRemove = null,
            onDismiss = { adding = false },
        )
    }
    editing?.let { id -> state.month?.lines?.firstOrNull { it.bill.id == id } }?.let { line ->
        BillSheet(
            initial = line.bill,
            onSave = { draft ->
                editing = null
                onEvent(BillsEvent.Update(line.bill.id, draft))
            },
            onRemove = {
                editing = null
                onEvent(BillsEvent.Remove(line.bill.id))
            },
            onDismiss = { editing = null },
        )
    }
}

/** "November · this month", what the month's bills come to, and what is still to enter. */
@Composable
private fun TotalCard(month: BillsMonth) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    SectionCard {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(
                    if (month.current) R.string.bills_month_current else R.string.bills_month_past,
                    monthName(month.month, locale),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
            MoneyText(
                Money(month.total, month.currency),
                style = HeroNumberStyle,
                fontWeight = FontWeight.SemiBold,
            )
            val waiting = month.current && month.toEnter > 0
            Text(
                when {
                    !month.current -> stringResource(R.string.bills_past_kept)

                    waiting ->
                        pluralStringResource(R.plurals.bills_to_enter, month.toEnter, month.toEnter)

                    month.lines.isEmpty() -> stringResource(R.string.bills_none_yet)

                    else -> stringResource(R.string.bills_all_in)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (waiting) FontWeight.SemiBold else FontWeight.Normal,
                color = if (waiting) colors.overText else colors.ink2,
            )
        }
    }
}

/**
 * A bill in the month: its name and kind (the name opens it), and its amount: a button while
 * it can change ("Enter amount" when a variable bill has none yet), gray with a lock once closed.
 */
@Composable
private fun BillRow(
    line: BillLine,
    currency: String,
    monthName: String,
    onNameClick: () -> Unit,
    onAmountClick: () -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val usual = line.bill.amount?.let { formatMoney(Money(it, currency), locale) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onNameClick)
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                line.bill.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                monoNumbers(
                    when {
                        line.bill.kind == BillKind.VARIABLE ->
                            stringResource(R.string.bills_kind_variable)

                        line.changed -> stringResource(R.string.bills_kind_changed, usual.orEmpty())

                        else -> stringResource(R.string.bills_kind_fixed, usual.orEmpty())
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (line.changed) FontWeight.SemiBold else FontWeight.Normal,
                color = if (line.changed) colors.compareText else colors.ink2,
            )
        }
        val amount = line.amount
        when {
            !line.editable -> {
                val description = amount?.let { formatMoney(Money(it, currency), locale) }
                    ?: stringResource(R.string.bills_not_entered)
                Row(
                    modifier = Modifier.semantics {
                        contentDescription = description + ", " + monthName
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.guide,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        monoNumbers(description),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink2,
                    )
                }
            }

            amount == null -> Button(
                onClick = onAmountClick,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.page,
                ),
            ) { Text(stringResource(R.string.bills_enter)) }

            else -> Text(
                monoNumbers(formatMoney(Money(amount, currency), locale)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (line.changed) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(
                        onClickLabel = stringResource(R.string.bills_change),
                        onClick = onAmountClick,
                    )
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The month's amount of [line]: for a fixed bill "Back to its usual amount" removes the month's
 * own, for a variable one it clears what was entered.
 */
@Composable
private fun AmountEditor(
    line: BillLine,
    month: BillsMonth,
    onEvent: (BillsEvent) -> Unit,
    onClose: () -> Unit,
) {
    val fixed = line.bill.kind == BillKind.FIXED
    val removable = if (fixed) line.changed else line.amount != null
    AmountDialog(
        title = line.bill.name,
        description = stringResource(
            if (fixed) R.string.bills_amount_fixed else R.string.bills_amount_variable,
            monthName(month.month, appLocale()),
        ),
        initial = line.amount ?: line.bill.amount,
        currency = month.currency,
        onConfirm = {
            onClose()
            onEvent(BillsEvent.SetAmount(line.bill.id, it))
        },
        onDismiss = onClose,
        onRemove = if (removable) {
            {
                onClose()
                onEvent(BillsEvent.SetAmount(line.bill.id, null))
            }
        } else {
            null
        },
    )
}
