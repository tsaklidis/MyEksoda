package io.eksoda.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.component.EksodaButton
import io.eksoda.core.designsystem.component.OverlineText
import io.eksoda.core.designsystem.theme.DisplayTitleStyle
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.currencySymbol
import kotlinx.coroutines.launch

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(state = state, onEvent = viewModel::onEvent)
}

/** The pages, in order. Only the last one writes anything (OnboardingViewModel). */
private enum class Page { WELCOME, WAYS, PRIVACY, HOUSEHOLD }

/**
 * Four pages: what Eksoda does, the three ways to add an expense, the privacy promise, then the
 * household (its name and, optionally, a monthly budget). The intro pages can be skipped.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val pages = Page.entries
    val pager = rememberPagerState(initialPage = initialPage) { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Surface(modifier = modifier.fillMaxSize(), color = EksodaTheme.colors.page) {
        Column(modifier = Modifier.safeDrawingPadding().imePadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (!last) {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = EksodaTheme.colors.ink2,
                        ),
                        onClick = {
                            scope.launch { pager.animateScrollToPage(pages.lastIndex) }
                        },
                    ) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) { index ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (pages[index]) {
                        Page.WELCOME -> WelcomePage()
                        Page.WAYS -> WaysPage()
                        Page.PRIVACY -> PrivacyPage()
                        Page.HOUSEHOLD -> HouseholdPage(state, onEvent)
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PageDots(pager)
                if (last) {
                    EksodaButton(
                        onClick = { onEvent(OnboardingEvent.Create) },
                        text = stringResource(R.string.onboarding_create),
                        enabled = state.canCreate,
                        isLoading = state.creating,
                    )
                } else {
                    EksodaButton(
                        onClick = {
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                        text = stringResource(R.string.onboarding_next),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.WelcomePage() {
    PageIcon(Icons.Outlined.QrCodeScanner)
    OverlineText(stringResource(R.string.onboarding_title))
    PageTitle(stringResource(R.string.onboarding_welcome_title))
    PageBody(stringResource(R.string.onboarding_welcome_body))
}

@Composable
private fun ColumnScope.WaysPage() {
    PageTitle(stringResource(R.string.onboarding_ways_title))
    WayRow(
        Icons.Outlined.QrCodeScanner,
        stringResource(R.string.onboarding_way_scan),
        stringResource(R.string.onboarding_way_scan_body),
    )
    WayRow(
        Icons.Outlined.Share,
        stringResource(R.string.onboarding_way_share),
        stringResource(R.string.onboarding_way_share_body),
    )
    WayRow(
        Icons.Outlined.EditNote,
        stringResource(R.string.onboarding_way_manual),
        stringResource(R.string.onboarding_way_manual_body),
    )
}

@Composable
private fun ColumnScope.PrivacyPage() {
    PageIcon(Icons.Outlined.Lock)
    PageTitle(stringResource(R.string.onboarding_promise_title))
    PageBody(stringResource(R.string.onboarding_promise_body))
}

@Composable
private fun ColumnScope.HouseholdPage(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    PageTitle(stringResource(R.string.onboarding_create_title))
    OutlinedTextField(
        value = state.name,
        onValueChange = { onEvent(OnboardingEvent.NameChanged(it)) },
        label = { Text(stringResource(R.string.onboarding_household_name)) },
        placeholder = { Text(stringResource(R.string.onboarding_household_name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.budget,
        onValueChange = { onEvent(OnboardingEvent.BudgetChanged(it)) },
        label = { Text(stringResource(R.string.onboarding_budget)) },
        suffix = { Text(currencySymbol(state.currency, appLocale())) },
        supportingText = {
            Text(
                stringResource(
                    if (state.budgetInvalid) {
                        R.string.onboarding_budget_invalid
                    } else {
                        R.string.onboarding_budget_hint
                    },
                ),
            )
        },
        isError = state.budgetInvalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onEvent(OnboardingEvent.Create) }),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        // The time zone (Europe/Athens) is not shown or chosen for now.
        text = stringResource(R.string.onboarding_defaults, state.currency),
        style = MaterialTheme.typography.bodySmall,
        color = EksodaTheme.colors.ink2,
    )
    if (state.failed) {
        Text(
            text = stringResource(R.string.onboarding_failed),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** A page's picture: its icon on a round neutral tile. */
@Composable
private fun PageIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(72.dp)
            .background(EksodaTheme.colors.tile, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = EksodaTheme.colors.ink,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(
        text = text,
        style = DisplayTitleStyle,
        color = EksodaTheme.colors.ink,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun PageBody(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = EksodaTheme.colors.ink2)
}

/** One way to add an expense: its icon on a tile, a name and a line about it. */
@Composable
private fun WayRow(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(48.dp).background(EksodaTheme.colors.tile, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = EksodaTheme.colors.ink)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = EksodaTheme.colors.ink,
            )
            Text(body, style = MaterialTheme.typography.bodyMedium, color = EksodaTheme.colors.ink2)
        }
    }
}

/** Where the user is: one dot per page, the current one a wider ink pill. */
@Composable
private fun PageDots(pager: PagerState) {
    val colors = EksodaTheme.colors
    val description = stringResource(
        R.string.onboarding_page_of,
        pager.currentPage + 1,
        pager.pageCount,
    )
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(pager.pageCount) { index ->
            val current = index == pager.currentPage
            Box(
                modifier = Modifier
                    .size(height = 6.dp, width = if (current) 20.dp else 6.dp)
                    .background(if (current) colors.ink else colors.chartNeutral, CircleShape),
            )
        }
    }
}

@Preview(name = "Welcome")
@Composable
private fun OnboardingWelcomePreview() {
    EksodaTheme(darkTheme = false) {
        OnboardingScreen(state = OnboardingUiState(), onEvent = {})
    }
}

@Preview(name = "Household, Greek", locale = "el")
@Composable
private fun OnboardingHouseholdPreview() {
    EksodaTheme(darkTheme = false) {
        OnboardingScreen(
            state = OnboardingUiState(name = "Σπίτι", budget = "600"),
            onEvent = {},
            initialPage = Page.HOUSEHOLD.ordinal,
        )
    }
}
