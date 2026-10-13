plugins {
    id("xfin.android.library")
    id("xfin.hilt")
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
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
