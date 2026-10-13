plugins {
    id("xfin.android.feature")
}

android {
    namespace = "com.xfin.feature.library"
}

dependencies {
    implementation(project(":data:jellyfin"))
    implementation(project(":data:sources"))
    implementation(libs.paging.compose)
}
