// Shared by the feature modules' unit tests (eksoda.android.feature adds it as a test
// dependency): the main-dispatcher rule and in-memory fakes of core:data's repositories.
// Never a dependency of production code.
plugins {
    alias(libs.plugins.eksoda.android.library)
}

dependencies {
    api(project(":core:data"))
    api(project(":core:model"))
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.paging.common)
}
