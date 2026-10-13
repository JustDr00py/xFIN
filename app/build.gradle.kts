plugins {
    id("xfin.android.application")
    id("xfin.android.compose")
    id("xfin.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.xfin.app"

    defaultConfig {
        applicationId = "com.xfin.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
