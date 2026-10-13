plugins {
    id("xfin.android.feature")
}

android {
    namespace = "com.xfin.feature.guide"
}

dependencies {
    implementation(project(":data:sources"))
    implementation(libs.paging.compose)
}
