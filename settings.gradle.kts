pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Eksoda"

include(":app")
include(":core:engine")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":feature:onboarding")
include(":feature:home")
include(":feature:scan")
include(":feature:receipts")
include(":feature:insights")
include(":feature:products")
include(":feature:household")
include(":feature:settings")
