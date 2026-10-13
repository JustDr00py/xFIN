pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "xFIN"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:database")
include(":core:network")
include(":core:player")
include(":data:iptv")
include(":data:sources")
include(":data:jellyfin")
include(":data:library")
include(":feature:onboarding")
include(":feature:livetv")
include(":feature:guide")
include(":feature:library")
include(":feature:player")
include(":feature:settings")
