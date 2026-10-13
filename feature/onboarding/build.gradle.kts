plugins {
    id("xfin.android.feature")
}

android {
    namespace = "com.xfin.feature.onboarding"
}

dependencies {
    implementation(project(":data:sources"))
    implementation(project(":data:jellyfin"))
}
