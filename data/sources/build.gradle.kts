plugins {
    id("xfin.android.library")
    id("xfin.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.xfin.data.sources"
}

dependencies {
    api(project(":core:model"))
    api(libs.paging.runtime)
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":data:iptv"))
    implementation(libs.kotlinx.serialization.json)
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
