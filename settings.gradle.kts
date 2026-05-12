pluginManagement {
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // cktap-android is consumed from the local Maven cache while the
        // upstream library is still pre-release. Drop this once the artifact
        // is published to Maven Central.
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "pos-satstack"
include(":app")
