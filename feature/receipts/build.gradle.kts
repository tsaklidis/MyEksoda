plugins {
    alias(libs.plugins.eksoda.android.feature)
}

dependencies {
    implementation(libs.androidx.paging.compose)
    // "Open original" opens the receipt page in a Custom Tab (docs/android-standalone.md §8.1).
    implementation(libs.androidx.browser)
}
