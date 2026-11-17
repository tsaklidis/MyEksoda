package io.eksoda.engine.providers

import io.eksoda.engine.providers.aadefhm.AADE_FHM
import io.eksoda.engine.providers.entersoft.ENTERSOFT
import io.eksoda.engine.providers.epsilon.EPSILON
import io.eksoda.engine.providers.impact.IMPACT

// Registered receipt sources. Adding a source: a package under providers/ with Urls, Parser and
// Sanitize files and a Provider value, an entry below, and a sanitized sample page with its
// expected JSON under testdata/fixtures/<provider>/.

public val REGISTRY: ProviderRegistry =
    ProviderRegistry(listOf(IMPACT, ENTERSOFT, AADE_FHM, EPSILON))
