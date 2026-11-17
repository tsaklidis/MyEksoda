package io.eksoda.core.data.di

import javax.inject.Qualifier

/** For database and network work. Inject it; never hard-code `Dispatchers.IO`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** For parsing and analytics combiners. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
