plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "tw.zhuoran.ime"
    compileSdk = 35
    defaultConfig {
        applicationId = "tw.zhuoran.ime"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.10.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
