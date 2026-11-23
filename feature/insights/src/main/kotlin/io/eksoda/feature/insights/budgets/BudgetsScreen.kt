package io.eksoda.feature.insights.budgets

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.component.SwitchRow
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.EverydayBudget
import io.eksoda.core.model.Money
import io.eksoda.core.ui.component.AmountDialog
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.monthName
import io.eksoda.feature.insights.R
import java.math.BigDecimal
import java.time.YearMonth

@Composable
fun BudgetsRoute(
    onBack: () -> Unit,
    onBillsClick: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.budgets_save_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BudgetsEffect.SaveFailed -> snackbarHostState.showSnackbar(saveFailed)
            }
        }
    }
    BudgetsScreen(state, viewModel::onEvent, onBack, onBillsClick, snackbarHostState)
}

private enum class Editing { DEFAULT, THIS_MONTH }

/**
 * The everyday budget: one amount for every month, and a switch to make the current month
 * different (a trip, a big month). Past months keep theirs; bills and category limits are set
 * elsewhere, which the notes at the bottom say.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    state: BudgetsUiState,
    onEvent: (BudgetsEvent) -> Unit,
    onBack: () -> Unit,
    onBillsClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = EksodaTheme.colors
    var editing by rememberSaveable { mutableStateOf<Editing?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budgets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.budgets_back),
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
        val everyday = state.everyday
        if (state.loading || everyday == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                DelayedProgressIndicator()
            }
            return@Scaffold
        }
        val locale = appLocale()
        val month = monthName(everyday.month, locale)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard {
                AmountRow(
                    label = stringResource(R.string.budgets_every_month),
                    supporting = stringResource(R.string.budgets_every_month_hint),
                    amount = everyday.default,
                    currency = everyday.currency,
                    onClick = { editing = Editing.DEFAULT },
                )
            }
            SectionCard {
                SwitchRow(
                    label = stringResource(R.string.budgets_this_month, month),
                    supporting = stringResource(R.string.budgets_this_month_hint),
                    checked = everyday.changed,
                    onCheckedChange = { on ->
                        if (on) {
                            editing = Editing.THIS_MONTH
                        } else {
                            onEvent(
                                BudgetsEvent.SetThisMonth(null),
                            )
                        }
                    },
                )
                if (everyday.changed) {
                    HorizontalDivider(color = colors.line)
                    AmountRow(
                        label = stringResource(R.string.budgets_month_only, month),
                        supporting = everyday.default?.let { default ->
                            stringResource(
                                R.string.budgets_back_to,
                                monthName(everyday.month.plusMonths(1), locale),
                                formatMoney(Money(default, everyday.currency), locale),
                            )
                        },
                        amount = everyday.thisMonth,
                        currency = everyday.currency,
                        onClick = { editing = Editing.THIS_MONTH },
                        accent = true,
                    )
                }
            }
            SectionCard {
                LinkRow(
                    label = stringResource(R.string.budgets_bills),
                    supporting = stringResource(R.string.budgets_bills_hint),
                    onClick = onBillsClick,
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Note(Icons.Outlined.Lock, stringResource(R.string.budgets_note_past))
                Note(Icons.Outlined.Category, stringResource(R.string.budgets_note_limits))
            }
        }
        editing?.let { which ->
            EditDialog(which, everyday, month, onEvent, onClose = { editing = null })
        }
    }
}

/** The amount dialog for the default ([Editing.DEFAULT]) or this month's amount. */
@Composable
private fun EditDialog(
    which: Editing,
    everyday: EverydayBudget,
    month: String,
    onEvent: (BudgetsEvent) -> Unit,
    onClose: () -> Unit,
) {
    val default = which == Editing.DEFAULT
    val set = { amount: BigDecimal? ->
        onClose()
        onEvent(if (default) BudgetsEvent.SetDefault(amount) else BudgetsEvent.SetThisMonth(amount))
    }
    val removable = if (default) everyday.default != null else everyday.changed
    AmountDialog(
        title = stringResource(R.string.budgets_dialog_title),
        description = if (default) {
            stringResource(R.string.budgets_every_month)
        } else {
            stringResource(R.string.budgets_month_only, month)
        },
        initial = if (default) everyday.default else everyday.thisMonth ?: everyday.default,
        currency = everyday.currency,
        onConfirm = { set(it) },
        onDismiss = onClose,
        onRemove = if (removable) ({ set(null) }) else null,
    )
}

@Composable
private fun AmountRow(
    label: String,
    supporting: String?,
    amount: BigDecimal?,
    currency: String,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    val colors = EksodaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
            }
        }
        if (amount != null) {
            MoneyText(
                Money(amount, currency),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (accent) colors.compareText else colors.ink,
            )
        } else {
            Text(
                stringResource(R.string.budgets_none),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, supporting: String, onClick: () -> Unit) {
    val colors = EksodaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.ink2,
        )
    }
}

@Composable
private fun Note(icon: ImageVector, text: String) {
    val colors = EksodaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
    }
}

@Preview
@Composable
private fun BudgetsScreenPreview() {
    EksodaTheme {
        BudgetsScreen(
            state = BudgetsUiState(
                loading = false,
                everyday = EverydayBudget(
                    currency = "EUR",
                    month = YearMonth.of(2026, 11),
                    default = BigDecimal("700"),
                    changed = true,
                    thisMonth = BigDecimal("850"),
                ),
            ),
            onEvent = {},
            onBack = {},
            onBillsClick = {},
        )
    }
}
