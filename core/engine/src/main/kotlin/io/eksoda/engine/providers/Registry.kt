// Port of backend/apps/receipts/providers/__init__.py.
package io.eksoda.engine.providers

import io.eksoda.engine.providers.aadefhm.AADE_FHM
import io.eksoda.engine.providers.entersoft.ENTERSOFT
import io.eksoda.engine.providers.impact.IMPACT

// Registered receipt sources. Adding a source: a package under providers/ with Urls, Parser and
// Sanitize files and a Provider value, an entry below, and the server's fixtures for it
// (docs/android-standalone.md §10.1). A new source is added to the Python backend first.

public val REGISTRY: ProviderRegistry = ProviderRegistry(listOf(IMPACT, ENTERSOFT, AADE_FHM))
