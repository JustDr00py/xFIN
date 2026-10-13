plugins {
    id("xfin.android.feature")
}

android {
    namespace = "com.xfin.feature.library"
}

dependencies {
    implementation(project(":data:jellyfin"))
    implementation(libs.paging.compose)
}
