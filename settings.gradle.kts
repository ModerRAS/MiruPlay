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

rootProject.name = "MiruPlay"

include(":app")
include(":core:model")
include(":core:common")
include(":media-source")
include(":cloud-drive")
include(":player-core")
include(":scanner")
include(":metadata")
include(":scraper")
include(":sync-engine")
include(":data")
include(":ui-tv")
include(":web-control")
