plugins {
    id("xfin.android.library")
    id("xfin.hilt")
}

android {
    namespace = "com.xfin.data.library"
}

dependencies {
    api(project(":core:model"))
    api(project(":data:jellyfin"))
    api(libs.paging.runtime)
    implementation(project(":data:sources"))
}
