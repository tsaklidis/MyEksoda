package io.eksoda.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Runs `viewModelScope` (Dispatchers.Main) on [dispatcher] for one test. Unconfined by default,
 * so a ViewModel's launched work has run by the time `onEvent` returns. `runTest` shares its
 * scheduler, so `advanceTimeBy` moves a ViewModel's debounce too.
 */
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) :
    TestWatcher() {
    // Set here as well as in starting(): a ViewModel the test class creates in a property is
    // built before rules run, and one that uses viewModelScope in its constructor captures the
    // main dispatcher then. Declare the rule first so this runs before those properties.
    init {
        Dispatchers.setMain(dispatcher)
    }

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/**
 * Collects [flow] for the rest of the test, as the screen would: a `WhileSubscribed` state
 * only updates while something collects it.
 */
fun TestScope.keepCollecting(flow: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}
