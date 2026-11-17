package io.eksoda.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    /** "Now" always comes from this clock, so tests can fix it. */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    // The one place that creates dispatchers; everything else injects them.
    @Suppress("InjectDispatcher")
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Suppress("InjectDispatcher")
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
