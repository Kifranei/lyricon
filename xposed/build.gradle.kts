
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    kotlin("plugin.serialization") version "2.1.21"
}

configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.xposed"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int)
    }

    defaultConfig {
        minSdk = rootProject.extra.get("minSdkVersion") as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "APP_PACKAGE_NAME",
            "\"${rootProject.extra["appPackageName"] as String}\""
        )
    }

    flavorDimensions += "locale"
    productFlavors {
        create("standard") {
            dimension = "locale"
        }
        create("zh") {
            dimension = "locale"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }
}

dependencies {
    implementation(project(":bridge"))
    implementation(project(":common"))
    implementation(project(":lyric:view"))
    implementation(project(":lyric:model"))
    implementation(project(":lyric:style"))
    implementation(project(":lyric:statusbarlyric"))
    implementation(project(":lyric:bridge:central"))
    "zhImplementation"(project(":opencc-lite"))

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
