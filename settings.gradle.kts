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
