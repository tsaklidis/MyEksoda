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
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.room.ktx)
    // Backup manifest and rows (backup/), built as JSON trees without the compiler plugin.
    implementation(libs.kotlinx.serialization.json)
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
            rule("core:data line coverage") {
                minBound(80)
            }
        }
    }
}
