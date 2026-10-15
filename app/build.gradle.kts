import java.util.Properties

plugins {
    id("xfin.android.application")
    id("xfin.android.compose")
    id("xfin.hilt")
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from keystore.properties in the project root (git-ignored):
// storeFile, storePassword, keyAlias, keyPassword. Without it, release builds are left unsigned.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "com.xfin.app"

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.xfin.app"
        versionCode = 3
        versionName = "0.3.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Release code (R8, not debuggable) signed with the debug key: for measuring real-world
        // performance on a device. Compose is several times slower in debuggable builds.
        create("profile") {
            initWith(getByName("release"))
            applicationIdSuffix = ".profile"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":data:sources"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:livetv"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:guide"))
    implementation(project(":feature:library"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tv.material)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.hilt.work)
}
