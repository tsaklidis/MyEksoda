plugins {
    alias(libs.plugins.eksoda.android.library)
    alias(libs.plugins.eksoda.hilt)
    alias(libs.plugins.kover)
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.paging.common)
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:database"))
    implementation(project(":core:engine"))
}

kover {
    reports {
        filters {
            excludes {
                // Hilt modules and the code Hilt and Dagger generate.
                packages("io.eksoda.core.data.di", "dagger.hilt.internal", "hilt_aggregated_deps")
                classes("*_Factory", "*_HiltModules*", "*_MembersInjector", "Hilt_*")
            }
        }
        verify {
            rule("core:data line coverage (docs/android-standalone.md §10.3)") {
                minBound(80)
            }
        }
    }
}
