plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "tw.zhuoran.ime"
    compileSdk = 35
    defaultConfig {
        applicationId = "tw.zhuoran.ime"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.12.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }

dependencies { testImplementation("junit:junit:4.13.2") }
