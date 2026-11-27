plugins {
    alias(libs.plugins.eksoda.android.feature)
}

android {
    // QrSampleSetTest reads hard QR samples with the real zxing-cpp, which runs on a device only.
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

dependencies {
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.paging.compose)
    // QR decoding on the device, with no network and no telemetry. Never ML Kit.
    implementation(libs.zxing.cpp)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.assertk)
    // Draws the sample QR codes.
    androidTestImplementation(libs.zxing.core)
}
