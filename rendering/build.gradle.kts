plugins {
    id("com.android.library")
}

android {
    namespace = "com.example.xfilm.rendering"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(project(":calculation"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)


    testImplementation(libs.junit)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.mockk.android)
}
