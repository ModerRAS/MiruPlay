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
include(":desktop-app")
include(":core:model")
include(":core:common")
include(":ui-design")
include(":repository-api")
include(":media-source-api")
include(":media-source")
include(":media-source-desktop")
include(":repository-desktop")
include(":cloud-drive-api")
include(":cloud-drive")
include(":cloud-drive-desktop")
include(":player-core")
include(":player-mpv")
include(":scanner")
include(":scanner-desktop")
include(":metadata-core")
include(":metadata")
include(":scraper-core")
include(":scraper")
include(":scraper-desktop")
include(":sync-engine")
include(":sync-engine-shared")
include(":sync-engine-desktop")
include(":data")
include(":ui-tv")
include(":web-control")
include(":web-control-core")
