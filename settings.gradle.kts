pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "SneakyPatches"

// Each included project is an independent patch bundled into the root JAR.
include(":early-item-merge-cancellation")
project(":early-item-merge-cancellation").projectDir = file("patches/early-item-merge-cancellation")
include(":resource-pack-reconciliation")
project(":resource-pack-reconciliation").projectDir = file("patches/resource-pack-reconciliation")
include(":tracking-range-categories")
project(":tracking-range-categories").projectDir = file("patches/tracking-range-categories")
include(":tracker-pair-recheck")
project(":tracker-pair-recheck").projectDir = file("patches/tracker-pair-recheck")
