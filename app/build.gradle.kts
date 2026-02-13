import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.1.21"
}

val versionCode: Int = rootProject.extra["appVersionCode"] as Int
val versionName: String = rootProject.extra["appVersionName"] as String
val buildTime: Long = System.currentTimeMillis()

configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.app"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = rootProject.extra.get("minSdkVersion") as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("int", "VERSION_CODE", versionCode.toString())
        buildConfigField("String", "VERSION_NAME", "\"" + versionName + "\"")
        buildConfigField("long", "BUILD_TIME", System.currentTimeMillis().toString())
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
    }
}

dependencies {
    implementation(project(":bridge"))
    implementation(project(":common"))
    implementation(project(":lyric:style"))
    implementation(project(":lyric:view"))
    implementation(libs.aboutlibraries.core)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    implementation(libs.miuix.android)
    implementation(libs.miuix.icons)

    implementation(libs.accompanist.drawablepainter)
    implementation(libs.androidx.browser)
    implementation(libs.chrisbanes.haze)
    implementation(libs.bonsai.core)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.backdrop)

    implementation(libs.capsule.android)
    implementation(libs.lottie.compose) {
        exclude(group = "androidx.appcompat", module = "appcompat")
    }

    // Xposed
    implementation(libs.yukihookapi.api)
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.extension)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.remote.creation.core)
    implementation(libs.androidx.appcompat.resources)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}