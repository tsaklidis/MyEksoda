plugins {
    alias(libs.plugins.eksoda.android.feature)
}

dependencies {
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.paging.compose)
    // QR decoding on the device, with no network and no telemetry. Never ML Kit.
    implementation(libs.zxing.cpp)
}
