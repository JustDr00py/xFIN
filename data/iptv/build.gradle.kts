plugins {
    id("xfin.android.library")
    id("xfin.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.xfin.data.iptv"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kxml2)
}
