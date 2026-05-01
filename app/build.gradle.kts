import com.android.build.api.dsl.LibraryExtension
import java.io.File

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.1.21"
}

val versionCode: Int = rootProject.extra["appVersionCode"] as Int
val versionName: String = rootProject.extra["appVersionName"] as String
val buildTime: Long = System.currentTimeMillis()

fun resolveGitDir(projectDir: File): File? {
    val dotGit = projectDir.resolve(".git")
    if (dotGit.isDirectory) return dotGit
    if (!dotGit.isFile) return null
    val refLine = dotGit.readText().lineSequence().firstOrNull()?.trim().orEmpty()
    if (!refLine.startsWith("gitdir:")) return null
    val gitDirPath = refLine.substringAfter("gitdir:").trim()
    if (gitDirPath.isBlank()) return null
    val resolved = File(gitDirPath).let { if (it.isAbsolute) it else projectDir.resolve(gitDirPath) }
    return resolved.takeIf { it.exists() }
}

fun readHeadRef(gitDir: File?): String {
    if (gitDir == null) return ""
    val headFile = gitDir.resolve("HEAD")
    if (!headFile.isFile) return ""
    val head = headFile.readText().trim()
    return if (head.startsWith("ref:")) head.substringAfter("ref:").trim() else ""
}

fun readHeadBranch(gitDir: File?): String {
    val ref = readHeadRef(gitDir)
    if (ref.isBlank()) return ""
    return ref.removePrefix("refs/heads/")
}

fun readHeadCommit(gitDir: File?): String {
    if (gitDir == null) return ""
    val headFile = gitDir.resolve("HEAD")
    if (!headFile.isFile) return ""
    val head = headFile.readText().trim()
    if (!head.startsWith("ref:")) {
        return head.take(7)
    }

    val refPath = head.substringAfter("ref:").trim()
    if (refPath.isBlank()) return ""
    val refFile = gitDir.resolve(refPath)
    if (refFile.isFile) {
        return refFile.readText().trim().take(7)
    }

    val packedRefs = gitDir.resolve("packed-refs")
    if (!packedRefs.isFile) return ""
    val packed = packedRefs.readLines()
        .asSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("^") }
        .map { it.trim().split(" ", limit = 2) }
        .firstOrNull { it.size == 2 && it[1] == refPath }
    return packed?.firstOrNull()?.take(7).orEmpty()
}

fun readOriginUrl(gitDir: File?): String {
    if (gitDir == null) return ""
    val configFile = gitDir.resolve("config")
    if (!configFile.isFile) return ""

    var inOriginSection = false
    for (raw in configFile.readLines()) {
        val line = raw.trim()
        when {
            line.startsWith("[") && line.endsWith("]") -> {
                inOriginSection = line == "[remote \"origin\"]"
            }

            inOriginSection && line.startsWith("url") -> {
                val value = line.substringAfter("=", "").trim()
                if (value.isNotBlank()) return value
            }
        }
    }
    return ""
}

fun resolveBuildMeta(propName: String, envName: String, fallback: () -> String): String {
    val fromProp = providers.gradleProperty(propName).orNull?.trim().orEmpty()
    if (fromProp.isNotEmpty()) return fromProp
    val fromEnv = providers.environmentVariable(envName).orNull?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv
    return fallback().trim().ifEmpty { "unknown" }
}

val gitDir = resolveGitDir(rootProject.projectDir)
val gitRemoteOrigin = readOriginUrl(gitDir)
val originRepo = Regex("[:/]([^/:]+/[^/]+?)(?:\\.git)?$")
    .find(gitRemoteOrigin)
    ?.groupValues
    ?.getOrNull(1)
    .orEmpty()
val originOwner = originRepo.substringBefore('/', missingDelimiterValue = "").ifBlank { "unknown" }


configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.app"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int) {
           // minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = rootProject.extra.get("minSdkVersion") as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("int", "VERSION_CODE", versionCode.toString())
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        buildConfigField("long", "BUILD_TIME", "${buildTime}L")
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
    // --- 模块依赖 ---
    implementation(project(":bridge"))
    implementation(project(":common"))
    implementation(project(":lyric:style"))
    implementation(project(":lyric:view"))

    // --- 第三方 UI 库 ---
    implementation(libs.miuix.android)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)

    implementation(libs.aboutlibraries.core)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.backdrop)
    implementation(libs.chrisbanes.haze)
    implementation(libs.bonsai.core)
    implementation(libs.capsule.android)
    implementation(libs.lottie.compose) {
        exclude(group = "androidx.appcompat", module = "appcompat")
    }

    // --- 核心逻辑与 AndroidX ---
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- Jetpack Compose (通过 BOM 管理) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)


    // --- 单元测试 ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // --- 调试工具 ---
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
