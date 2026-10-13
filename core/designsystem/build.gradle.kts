plugins {
    id("xfin.android.library")
    id("xfin.android.compose")
}

android {
    namespace = "com.xfin.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(libs.coil.compose)
    implementation(libs.tv.material)
}
