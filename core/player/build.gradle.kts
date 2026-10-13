plugins {
    id("xfin.android.library")
    id("xfin.hilt")
}

android {
    namespace = "com.xfin.core.player"
}

dependencies {
    api(project(":core:model"))
    api(libs.media3.exoplayer)
    implementation(project(":core:network"))
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ffmpeg.decoder)
}
