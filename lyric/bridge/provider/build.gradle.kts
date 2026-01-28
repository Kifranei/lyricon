import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    kotlin("plugin.serialization") version "2.1.21"
    id("kotlin-parcelize")
    signing
    id("com.vanniktech.maven.publish")
}

val version: String = rootProject.extra.get("providerSdkVersion") as String

configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.provider"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    api(project(":lyric:model"))
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat.resources)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    coordinates(
        "io.github.proify.lyricon",
        "provider",
        version
    )

    pom {
        name.set("provider")
        description.set("Provide lyrics services for Lyricon")
        inceptionYear.set("2025")
        url.set("https://github.com/proify/lyricon")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("Proify")
                name.set("Proify")
                url.set("https://github.com/proify")
            }
        }
        scm {
            url.set("https://github.com/proify/lyricon")
            connection.set("scm:git:git://github.com/proify/lyricon.git")
            developerConnection.set("scm:git:ssh://git@github.com/proify/lyricon.git")
        }
    }
    publishToMavenCentral()
    signAllPublications()
}

afterEvaluate {
    signing {
        useGpgCmd()
    }
}