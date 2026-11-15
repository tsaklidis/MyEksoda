plugins {
    alias(libs.plugins.eksoda.android.library)
    alias(libs.plugins.eksoda.android.compose)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.compose.material.icons.extended)
}
