package io.eksoda.feature.home.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.component.EksodaButton
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.Bill
import io.eksoda.core.model.BillKind
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.formatDecimalInput
import io.eksoda.core.ui.text.parseMoneyInput
import io.eksoda.feature.home.R

/**
 * A new bill ([initial] null) or one to edit: its name, whether its amount changes (fixed or
 * variable), and its amount: every month for a fixed one, "usually about" (optional) for a
 * variable one. A new amount applies from this month on. [onRemove] stops an existing bill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BillSheet(
    initial: Bill?,
    onSave: (BillDraft) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: BillKind.FIXED) }
    var amountText by rememberSaveable {
        mutableStateOf(initial?.amount?.let { formatDecimalInput(it, locale) }.orEmpty())
    }
    val amount = parseMoneyInput(amountText)?.takeIf { it.signum() > 0 }
    val amountWrong = amountText.isNotBlank() && amount == null
    val canSave = name.isNotBlank() && !amountWrong && (kind == BillKind.VARIABLE || amount != null)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.panel,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(if (initial == null) R.string.bills_new else R.string.bills_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.bills_name)) },
                placeholder = { Text(stringResource(R.string.bills_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.bills_kind_question),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
            Column(modifier = Modifier.selectableGroup()) {
                KindRow(
                    title = stringResource(R.string.bills_fixed),
                    text = stringResource(R.string.bills_fixed_hint),
                    selected = kind == BillKind.FIXED,
                    onClick = { kind = BillKind.FIXED },
                )
                KindRow(
                    title = stringResource(R.string.bills_variable),
                    text = stringResource(R.string.bills_variable_hint),
                    selected = kind == BillKind.VARIABLE,
                    onClick = { kind = BillKind.VARIABLE },
                )
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = {
                    Text(
                        stringResource(
                            if (kind ==
                                BillKind.FIXED
                            ) {
                                R.string.bills_every_month
                            } else {
                                R.string.bills_usually
                            },
                        ),
                    )
                },
                suffix = { Text("€") },
                isError = amountWrong,
                supportingText = {
                    Text(
                        stringResource(
                            if (kind ==
                                BillKind.FIXED
                            ) {
                                R.string.bills_from_now_on
                            } else {
                                R.string.bills_usually_hint
                            },
                        ),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            EksodaButton(
                onClick = { onSave(BillDraft(name.trim(), kind, amount)) },
                text = stringResource(R.string.bills_save),
                enabled = canSave,
            )
            if (onRemove != null) {
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bills_remove), color = colors.overText)
                }
            }
        }
    }
}

@Composable
private fun KindRow(title: String, text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text, style = MaterialTheme.typography.bodySmall, color = EksodaTheme.colors.ink2)
        }
    }
}
