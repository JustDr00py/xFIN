plugins {
    id("xfin.android.library")
    id("xfin.hilt")
}

android {
    namespace = "com.xfin.data.jellyfin"
}

dependencies {
    api(project(":core:model"))
    api(libs.paging.runtime)
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(libs.jellyfin.sdk)
    // The SDK logs through kotlin-logging, which calls SLF4J at runtime but does not declare it.
    // Without a binding SLF4J falls back to a no-op logger.
    runtimeOnly(libs.slf4j.api)
}
