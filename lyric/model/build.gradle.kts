plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    signing
    id("com.vanniktech.maven.publish")
    kotlin("plugin.serialization") version "2.1.21"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

val version: String = rootProject.extra.get("lyricModelVersion") as String

mavenPublishing {
    coordinates(
        "io.github.proify.lyricon.lyric",
        "model",
        version
    )

    pom {
        name.set("model")
        description.set("model")
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