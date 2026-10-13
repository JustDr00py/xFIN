plugins {
    id("xfin.android.feature")
}

android {
    namespace = "com.xfin.feature.settings"
}

dependencies {
    implementation(project(":data:sources"))
}
