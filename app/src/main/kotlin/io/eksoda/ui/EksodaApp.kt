package io.eksoda.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.eksoda.R
import io.eksoda.core.ui.motion.LocalNavAnimatedVisibilityScope
import io.eksoda.core.ui.motion.LocalSharedTransitionScope
import io.eksoda.feature.onboarding.OnboardingRoute
import io.eksoda.feature.products.catalog.CategoriesRoute
import io.eksoda.feature.products.catalog.MerchantDetailRoute
import io.eksoda.feature.products.catalog.MerchantsRoute
import io.eksoda.feature.products.detail.ProductDetailRoute
import io.eksoda.feature.products.review.ReviewRoute
import io.eksoda.feature.receipts.detail.ReceiptDetailRoute
import io.eksoda.feature.scan.ScanRoute
import io.eksoda.feature.scan.imports.ImportListRoute
import io.eksoda.feature.scan.share.ShareImportDialog

/**
 * Onboarding until the household exists, then the app's navigation graph: the tabs
 * ([MainTabs]), and receipt detail, scan and imports above them without the tab bar.
 */
@Composable
fun EksodaApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launch by viewModel.launch.collectAsStateWithLifecycle()
    // Onboarding fades into the receipts once the household exists.
    Crossfade(targetState = state, label = "app state") { current ->
        when (current) {
            AppState.LOADING -> Surface(modifier = Modifier.fillMaxSize()) {}

            AppState.UNAVAILABLE -> DataUnavailable()

            AppState.ONBOARDING -> OnboardingRoute()

            AppState.READY ->
                MainNavHost(launch = launch, onLaunchHandled = viewModel::onLaunchHandled)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainNavHost(launch: AppLaunch?, onLaunchHandled: () -> Unit) {
    val navController = rememberNavController()
    val openReceipt: (String) -> Unit = { navController.navigate(ReceiptDetailDestination(it)) }
    val openProduct: (String) -> Unit = { navController.navigate(ProductDetailDestination(it)) }
    val back: () -> Unit = { navController.navigateUp() }

    // Shared elements (the merchant name and total) travel from a receipt row to its detail.
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(navController = navController, startDestination = MainDestination) {
                composable<MainDestination> {
                    WithNavAnimation {
                        MainTabs(
                            onReceiptClick = openReceipt,
                            onScanClick = { navController.navigate(ScanDestination) },
                            onImportsClick = { navController.navigate(ImportsDestination) },
                            onProductClick = openProduct,
                            onReviewClick = { navController.navigate(ReviewDestination) },
                            onCategoriesClick = { navController.navigate(CategoriesDestination) },
                            onMerchantsClick = { navController.navigate(MerchantsDestination) },
                        )
                    }
                }
                composable<ReceiptDetailDestination> {
                    WithNavAnimation {
                        ReceiptDetailRoute(
                            onBack = back,
                            onProductClick = openProduct,
                            onMerchantClick = {
                                navController.navigate(MerchantDetailDestination(it))
                            },
                        )
                    }
                }
                composable<ProductDetailDestination> {
                    ProductDetailRoute(onBack = back, onReceiptClick = openReceipt)
                }
                composable<ReviewDestination> {
                    ReviewRoute(onBack = back, onReceiptClick = openReceipt)
                }
                composable<CategoriesDestination> { CategoriesRoute(onBack = back) }
                composable<MerchantsDestination> {
                    MerchantsRoute(
                        onBack = back,
                        onMerchantClick = { merchantId ->
                            navController.navigate(MerchantDetailDestination(merchantId))
                        },
                    )
                }
                composable<MerchantDetailDestination> { MerchantDetailRoute(onBack = back) }
                composable<ScanDestination> {
                    ScanRoute(
                        onClose = { navController.navigateUp() },
                        onShowImports = { navController.replaceScanWith(ImportsDestination) },
                        onViewReceipt = openReceipt,
                    )
                }
                composable<ImportsDestination> {
                    ImportListRoute(
                        onBack = { navController.navigateUp() },
                        onReceiptClick = openReceipt,
                    )
                }
            }
        }
    }

    LaunchedEffect(launch) {
        if (launch is AppLaunch.OpenReceipt) {
            openReceipt(launch.receiptId)
            onLaunchHandled()
        }
    }
    if (launch is AppLaunch.Share) {
        ShareImportDialog(
            sharedText = launch.text,
            onDismiss = onLaunchHandled,
            onViewReceipt = { receiptId ->
                onLaunchHandled()
                openReceipt(receiptId)
            },
        )
    }
}

/** Lets the screen's shared elements follow this destination's enter and exit animation. */
@Composable
private fun AnimatedContentScope.WithNavAnimation(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this, content = content)
}

/** "Done" on the scan screen opens the imports list in its place. */
private fun NavHostController.replaceScanWith(destination: Any) {
    navigate(destination) {
        popUpTo<ScanDestination> { inclusive = true }
    }
}

@Composable
private fun DataUnavailable() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.data_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
