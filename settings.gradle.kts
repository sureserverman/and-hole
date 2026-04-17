pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pihole-android"

include(":app")
include(":core:dns")
include(":core:filter")
include(":core:upstream")
include(":core:tor")
include(":data")
include(":feature:home")
include(":feature:lists")
include(":feature:rules")
include(":feature:logs")
include(":feature:settings")
include(":feature:diagnostics")
