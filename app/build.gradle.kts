plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.elyedali.makhzan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elyedali.makhzan"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }

    // مفتاح التوقيع الثابت: يأتي من أسرار GitHub (KEYSTORE_BASE64 و KEYSTORE_PASSWORD)
    val keystoreFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/makhzan-upload-key.jks")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val hasKeystore = keystoreFile.exists() && !keystorePassword.isNullOrEmpty()

    signingConfigs {
        if (hasKeystore) {
            create("upload") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "upload"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasKeystore) signingConfigs.getByName("upload") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // قاعدة البيانات المحلية (تعمل دون إنترنت)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // الكاميرا وقراءة الكود بار (النموذج مدمج في التطبيق، يعمل دون إنترنت)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}
