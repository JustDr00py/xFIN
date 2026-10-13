plugins {
    id("xfin.android.library")
    id("xfin.hilt")
}

android {
    namespace = "com.xfin.core.network"
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)
}
