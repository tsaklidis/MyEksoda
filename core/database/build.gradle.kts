plugins {
    alias(libs.plugins.eksoda.android.library)
    alias(libs.plugins.eksoda.android.room)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
}
