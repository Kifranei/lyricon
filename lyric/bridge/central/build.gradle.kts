import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    signing
    id("com.vanniktech.maven.publish")
}

val version: String = rootProject.extra.get("providerSdkVersion") as String

configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.central"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int){
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 27

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":lyric:bridge:provider"))

    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

publishing {
    repositories {
        maven {
            name = "fastmcmirror"
            url = uri("https://repo.fastmcmirror.org/content/repositories/releases/")
            credentials(PasswordCredentials::class)
        }
    }
}

mavenPublishing {
    coordinates(
        "io.github.proify.lyricon",
        "central",
        version
    )

    pom {
        name.set("central")
        description.set("Manage lyrics providers for Lyricon")
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
    signAllPublications()
}

afterEvaluate {
    signing {
        useGpgCmd()
    }
}