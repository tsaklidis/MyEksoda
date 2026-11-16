package io.eksoda.feature.products.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.model.CategorySummary
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.text.categoryName
import io.eksoda.feature.products.R
import io.eksoda.feature.products.common.CategoryBadge
import io.eksoda.feature.products.common.catalogErrorRes

/** A category being created (no id) or renamed. */
private data class NameDialog(val id: String?, val name: String)

/** Categories (`android-app.md` §6.9): create, rename and delete the household's own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesRoute(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<NameDialog?>(null) }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                CategoriesEffect.Deleted -> resources.getString(R.string.category_deleted)
                is CategoriesEffect.Failed -> resources.getString(catalogErrorRes(effect.error))
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = NameDialog(id = null, name = "") }) {
                Icon(Icons.Default.Add, stringResource(R.string.category_new))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val list = categories
            if (list == null) {
                DelayedProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                CategoryList(list) { dialog = NameDialog(it.category.id, it.category.name) }
            }
        }
    }
    dialog?.let { current ->
        CategoryNameDialog(
            initial = current.name,
            editing = current.id != null,
            onSave = { name ->
                dialog = null
                val event = current.id?.let { CategoriesEvent.Rename(it, name) }
                    ?: CategoriesEvent.Create(name)
                viewModel.onEvent(event)
            },
            onDelete = {
                dialog = null
                current.id?.let { viewModel.onEvent(CategoriesEvent.Delete(it)) }
            },
            onDismiss = { dialog = null },
        )
    }
}

/** The household's categories, then the system ones (locked), each by name. */
@Composable
private fun CategoryList(categories: List<CategorySummary>, onEdit: (CategorySummary) -> Unit) {
    val named = categories.map { it to categoryName(it.category) }
    val (system, own) = named.sortedBy { it.second.lowercase() }
        .partition { it.first.category.isSystem }
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        if (own.isNotEmpty()) {
            item(key = "own") { SectionTitle(R.string.categories_own) }
            items(own, key = { it.first.category.id }) { (summary, name) ->
                CategoryRow(summary, name, onClick = { onEdit(summary) })
            }
        }
        item(key = "system") { SectionTitle(R.string.categories_system) }
        items(system, key = { it.first.category.id }) { (summary, name) ->
            CategoryRow(summary, name, onClick = null)
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun CategoryRow(summary: CategorySummary, name: String, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = {
            Text(
                pluralStringResource(
                    R.plurals.category_products,
                    summary.productCount,
                    summary.productCount,
                ),
            )
        },
        leadingContent = { CategoryBadge(summary.category.slug) },
        trailingContent = if (onClick == null) {
            {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.category_locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun CategoryNameDialog(
    initial: String,
    editing: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editing) R.string.category_rename else R.string.category_new))
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.category_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            if (editing) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.delete)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
