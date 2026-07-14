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
include(":background-task")
include(":core:model")
include(":core:common")
include(":ui-design")
include(":repository-api")
include(":media-source-api")
include(":media-source")
include(":cloud-drive-api")
include(":cloud-drive-core")
include(":cloud-drive")
include(":player-core")
include(":player-mpv-android")
include(":scanner")
include(":metadata-core")
include(":metadata")
include(":scraper-core")
include(":scraper")
include(":sync-engine")
include(":sync-engine-shared")
include(":data")
include(":ui-tv")
include(":web-control")
include(":web-control-core")
