package io.eksoda.feature.scan

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** The photo reader the scan screen uses: zxing-cpp on the device ([QrImageDecoder]). */
@Module
@InstallIn(ViewModelComponent::class)
internal interface ScanModule {
    @Binds
    fun photoQrReader(decoder: QrImageDecoder): PhotoQrReader
}
