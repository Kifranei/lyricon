@file:Suppress("UnstableApiUsage")

include(":lyric:statusbarlyric")


pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://api.xposed.info/") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
        maven { url = uri("https://api.xposed.info/") }
    }
}

include(
    ":lyricon",
    ":app",
    ":bridge",
    ":xposed",
    ":common",
)

include(":lyric:bridge:central")
include(":lyric:bridge:provider")
include(":lyric:bridge:localcentralapp")

include(":lyric:model")
include(":lyric:view")
include(":lyric:style")
include(":lyric:viewAppTest")

rootProject.name = "LyriconProject"