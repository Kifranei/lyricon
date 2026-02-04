plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false

    id("com.vanniktech.maven.publish") version "0.36.0" apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

extra["appPackageName"] = "io.github.proify.lyricon"
extra["appVersionCode"] = 12
extra["appVersionName"] = "1.0.0-beta4"
extra["compileSdkVersion"] = 36
extra["targetSdkVersion"] = 36
extra["minSdkVersion"] = 28
extra["buildTime"] = System.currentTimeMillis()

extra["providerSdkVersion"] = "0.1.68"
extra["lyricModelVersion"] = "0.1.68"