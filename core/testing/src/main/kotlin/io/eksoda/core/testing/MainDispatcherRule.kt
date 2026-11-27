package io.eksoda.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Runs `viewModelScope` (Dispatchers.Main) on [dispatcher] for one test. Unconfined by default,
 * so a ViewModel's launched work has run by the time `onEvent` returns; pass the test's own
 * `StandardTestDispatcher` to step through it instead.
 */
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) :
    TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
