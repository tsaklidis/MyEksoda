package io.eksoda.feature.receipts.manual

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.component.EksodaButton
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.HeroNumberStyle
import io.eksoda.core.designsystem.theme.PillShape
import io.eksoda.core.model.ManualExpense
import io.eksoda.core.model.ManualKind
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.categoryName
import io.eksoda.core.ui.text.currencySymbol
import io.eksoda.core.ui.text.dayLabel
import io.eksoda.core.ui.text.formatDecimalInput
import io.eksoda.core.ui.text.parseMoneyInput
import io.eksoda.core.ui.text.searchKey
import io.eksoda.feature.receipts.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ManualExpenseRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ManualExpenseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val failed = stringResource(R.string.manual_save_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ManualExpenseEffect.Saved -> onSaved()
                ManualExpenseEffect.Failed -> snackbarHostState.showSnackbar(failed)
            }
        }
    }
    ManualExpenseScreen(state, viewModel::onEvent, onBack, snackbarHostState)
}

/**
 * An expense typed in by hand (docs/design/ui-redesign.md §5): the amount first and large, then
 * what it was (a product, a service or something else) in a few words, a category if one fits,
 * and the day. Only the amount and the words are needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualExpenseScreen(
    state: ManualExpenseUiState,
    onEvent: (ManualExpenseEvent) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    val title =
                        if (state.editing) R.string.manual_edit_title else R.string.manual_title
                    Text(stringResource(title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val initial = state.initial
        val today = state.today
        if (state.loading || initial == null || today == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                DelayedProgressIndicator()
            }
            return@Scaffold
        }
        ManualForm(
            state = state,
            initial = initial,
            today = today,
            onSave = { onEvent(ManualExpenseEvent.Save(it)) },
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualForm(
    state: ManualExpenseUiState,
    initial: ManualDraft,
    today: LocalDate,
    onSave: (ManualExpense) -> Unit,
    modifier: Modifier,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    // The fields live here while they are typed (never fed back through a Flow).
    var amountText by rememberSaveable {
        mutableStateOf(initial.amount?.let { formatDecimalInput(it, locale) }.orEmpty())
    }
    var kind by rememberSaveable { mutableStateOf(initial.kind) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var categoryId by rememberSaveable { mutableStateOf(initial.categoryId) }
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var allCategories by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val amount = parseMoneyInput(amountText)?.takeIf { it.signum() > 0 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initial.amount == null) focus.requestFocus() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val wrongAmount = amountText.isNotBlank() && amount == null
        AmountField(amountText, { amountText = it }, focus, wrongAmount)
        KindTabs(kind) { kind = it }
        DescriptionField(kind, description, state.suggestions) { description = it }
        Text(
            stringResource(R.string.manual_category),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink2,
        )
        val shown = if (allCategories) state.categories else state.categories.take(LIKELY_SHOWN)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val picked = state.categories.firstOrNull { it.id == categoryId }
            val chips = if (picked != null && picked !in shown) shown + picked else shown
            chips.forEach { category ->
                Chip(categoryName(category), selected = category.id == categoryId) {
                    // A second tap takes it back: the category is optional.
                    categoryId = if (categoryId == category.id) null else category.id
                }
            }
            if (!allCategories && state.categories.size > LIKELY_SHOWN) {
                TextButton(onClick = { allCategories = true }) {
                    Text(stringResource(R.string.manual_all_categories), color = colors.compareText)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { pickingDate = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = colors.ink2)
            Text(
                dayLabel(date, today),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.manual_change_date), color = colors.compareText)
        }
        EksodaButton(
            onClick = {
                onSave(
                    ManualExpense(
                        amount = checkNotNull(amount),
                        date = date,
                        kind = kind,
                        description = description.trim(),
                        categoryId = categoryId,
                        note = initial.note,
                    ),
                )
            },
            text = stringResource(R.string.manual_save),
            enabled = amount != null && description.isNotBlank(),
            isLoading = state.saving,
        )
    }
    if (pickingDate) {
        DateDialog(date, today, onPick = { date = it }, onDismiss = { pickingDate = false })
    }
}

/** The amount, large and centered, with the currency after it. */
@Composable
private fun AmountField(
    text: String,
    onChange: (String) -> Unit,
    focus: FocusRequester,
    wrong: Boolean,
) {
    val colors = EksodaTheme.colors
    val label = stringResource(R.string.manual_amount)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onChange,
            singleLine = true,
            textStyle = HeroNumberStyle.copy(
                color = if (wrong) colors.overText else colors.ink,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(colors.compare),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) {
                        Text("0", style = HeroNumberStyle, color = colors.guide)
                    }
                    field()
                }
            },
            modifier = Modifier
                .focusRequester(focus)
                .semantics { contentDescription = label }
                .width(IntrinsicSize.Min)
                .widthIn(min = 48.dp),
        )
        Text(
            " " + currencySymbol("EUR", appLocale()),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.ink2,
        )
    }
}

/** Product · Service · Other as text tabs with an underline, no boxes (§1). */
@Composable
private fun KindTabs(selected: ManualKind, onSelect: (ManualKind) -> Unit) {
    val colors = EksodaTheme.colors
    Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        ManualKind.entries.forEach { kind ->
            val on = kind == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(kind) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Text(
                    stringResource(kindLabel(kind)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) colors.ink else colors.ink2,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 3.dp)
                        .background(if (on) colors.ink else colors.line),
                )
            }
        }
    }
}

/**
 * What it was, in a few words: the label and example follow the kind ("Who did the work?",
 * "e.g. dentist, plumber"). Descriptions typed before that match are offered under it.
 */
@Composable
private fun DescriptionField(
    kind: ManualKind,
    text: String,
    suggestions: List<String>,
    onChange: (String) -> Unit,
) {
    val (label, hint) = when (kind) {
        ManualKind.PRODUCT -> R.string.manual_product_label to R.string.manual_product_hint
        ManualKind.SERVICE -> R.string.manual_service_label to R.string.manual_service_hint
        ManualKind.OTHER -> R.string.manual_other_label to R.string.manual_other_hint
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            label = { Text(stringResource(label)) },
            placeholder = { Text(stringResource(hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val key = searchKey(text)
        val matching = suggestions
            .filter { key.isEmpty() || searchKey(it).contains(key) }
            .filter { !it.equals(text.trim(), ignoreCase = true) }
            .take(MAX_SUGGESTIONS)
        if (matching.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                matching.forEach { suggestion ->
                    Chip(suggestion, selected = false) { onChange(suggestion) }
                }
            }
        }
    }
}

/** An outlined choice; the chosen one filled with ink. */
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = EksodaTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(PillShape)
            .background(if (selected) colors.ink else Color.Transparent)
            .border(1.dp, if (selected) colors.ink else colors.line, PillShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.page else colors.ink,
        )
    }
}

/** The day it was paid: today or before, never later. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    date: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val lastMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= lastMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.manual_date_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.manual_date_cancel)) }
        },
    ) {
        DatePicker(state = picker)
    }
}

private fun kindLabel(kind: ManualKind): Int = when (kind) {
    ManualKind.PRODUCT -> R.string.manual_kind_product
    ManualKind.SERVICE -> R.string.manual_kind_service
    ManualKind.OTHER -> R.string.manual_kind_other
}

/** How many categories show before "All categories". */
private const val LIKELY_SHOWN = 8
private const val MAX_SUGGESTIONS = 5
