import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Pixabay API key for the online image search: never hardcoded (this repo is public). Locally,
// put PIXABAY_API_KEY=... in local.properties (gitignored); CI supplies it via a Gradle
// property (-PPIXABAY_API_KEY=...) sourced from a GitHub Actions secret.
val pixabayApiKey: String = (project.findProperty("PIXABAY_API_KEY") as String?)
    ?: run {
        val localProps = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) localFile.inputStream().use { localProps.load(it) }
        localProps.getProperty("PIXABAY_API_KEY", "")
    }

android {
    namespace = "com.dskmusic.web2app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dskmusic.web2app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.5"
        buildConfigField("String", "PIXABAY_API_KEY", "\"$pixabayApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.recyclerview)
    implementation(libs.okhttp)
    implementation(libs.ucrop)
    implementation(libs.androidx.webkit)
}
