plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "tw.zhuoran.ime"
    compileSdk = 35
    ndkVersion = "28.2.13676358"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    sourceSets["main"].assets.srcDir("../vendor/assets")
    testOptions { unitTests.all { it.systemProperty("zhuoran.test.dictionary", rootProject.file("vendor/dictionary").absolutePath); it.jvmArgs("-Djava.library.path=" + rootProject.file("vendor/host").absolutePath) } }
    defaultConfig {
        applicationId = "tw.zhuoran.ime"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
        versionCode = 13
        versionName = "0.13.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }

dependencies {
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
