plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ua.vitaliylobko.marnotrat.widget"
    compileSdk = 35

    defaultConfig {
        applicationId = "ua.vitaliylobko.marnotrat.widget"
        minSdk = 31
        targetSdk = 35
        // Номер збірки з GitHub Actions: кожна нова ставиться поверх попередньої
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    }

    // Один ключ на всі збірки (секрет репозиторію): інший ключ — і Android не дасть оновити, лише перевстановити
    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_FILE")
            if (path != null) {
                storeFile = file(path)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
