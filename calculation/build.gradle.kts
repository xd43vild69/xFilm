plugins {
    id("com.android.library")
}

android {
    namespace = "com.example.xfilm.calculation"
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)


    testImplementation(libs.junit)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.mockk.android)
}
