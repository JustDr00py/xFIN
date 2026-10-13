plugins {
    id("xfin.android.feature")
}

android {
    namespace = "com.xfin.feature.player"
}

dependencies {
    implementation(project(":data:sources"))
    implementation(project(":core:player"))
    implementation(project(":data:jellyfin"))
    implementation(libs.media3.ui.compose)
    implementation(libs.paging.compose)
}
