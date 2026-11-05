import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseIdentity = Properties().apply {
    load(providers.fileContents(layout.projectDirectory.file("version.properties")).asText.get().reader())
}
val baseVersion = releaseIdentity.getProperty("versionName")
val buildIndex = releaseIdentity.getProperty("buildIndex")
require(baseVersion.matches(Regex("\\d+\\.\\d+\\.\\d+"))) { "版本号必须是三段数字" }
require(buildIndex.matches(Regex("[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}"))) { "修改索引必须是三组16位十六进制数" }

android {
    namespace = "com.example.douqiuqiu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.douqiuqiu"
        minSdk = 24
        targetSdk = 36
        versionCode = releaseIdentity.getProperty("versionCode").toInt()
        versionName = baseVersion
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev-$buildIndex"
        }
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
}
