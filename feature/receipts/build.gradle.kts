plugins {
    alias(libs.plugins.eksoda.android.feature)
}

dependencies {
    implementation(libs.androidx.paging.compose)
    // "Open original" opens the receipt page in a Custom Tab.
    implementation(libs.androidx.browser)
}
