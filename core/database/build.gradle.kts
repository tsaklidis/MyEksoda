plugins {
    alias(libs.plugins.eksoda.android.library)
    alias(libs.plugins.eksoda.android.room)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.sqlite)
    api(libs.room.runtime)
    api(libs.room.paging)
    api(libs.androidx.paging.common)
}
