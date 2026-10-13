plugins {
    id("xfin.android.library")
    id("xfin.hilt")
}

android {
    namespace = "com.xfin.core.database"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":core:model"))
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.room.paging)
    api(libs.paging.runtime)
    ksp(libs.room.compiler)
}
