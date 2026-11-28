import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    jacoco
}

// Ключ TMDB и флаг файлового логирования (D6) читаются из local.properties
// (файл в .gitignore) и не коммитятся.
val localPropertiesFile = rootProject.file("local.properties")
val localProperties: Properties =
    if (localPropertiesFile.exists()) {
        val props = Properties()
        localPropertiesFile.inputStream().use { props.load(it) }
        props
    } else {
        Properties()
    }
val tmdbApiKey: String = localProperties.getProperty("TMDB_API_KEY", "")

// D6 (docs/debugging.md): tmdb.logging=true — писать каждый запрос/ответ
// TMDB в файл на устройстве (по умолчанию выкл).
val tmdbLogging: Boolean = localProperties.getProperty("tmdb.logging", "false").toBoolean()

// D8 (docs/debugging.md): pairSource — источник пары (стратегия):
// auto | online | bank | remote (default auto). Банк (assets/pairs_bank_v1.json)
// используется в режимах auto и bank; без ассета/битым файлом авто-режим
// просто идёт в онлайн (без ошибки). Заменяет D7-флаг pairBank.enabled.
val pairSource: String = localProperties.getProperty("pairSource", "auto").trim().ifEmpty { "auto" }

android {
    namespace = "com.zurus.cinethread"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zurus.cinethread"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        // D6: файловый лог запросов/ответов TMDB (default false).
        buildConfigField("boolean", "TMDB_LOGGING", "$tmdbLogging")
        // D8: источник пары (auto|online|bank|remote), дефолт auto.
        buildConfigField("String", "PAIR_SOURCE", "\"$pairSource\"")
    }

    buildTypes {
        release {
            // docs/05_roadmap.md, Этап 6, пункт 1: R8-минификация release-сборки.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // D7: android.util.Log в unit-тестах (PairBankLoader)
            // возвращает дефолтные значения, а не бросает "not mocked".
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose / UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    // DI (Hilt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Утилиты
    implementation(libs.timber)

    // Сеть: TMDB (Retrofit + OkHttp + kotlinx-serialization)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    // Локальная база: Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Тесты (unit)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    kspTest(libs.hilt.compiler)

    // Тесты (instrumented)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Room / Retrofit / OkHttp / kotlinx-serialization / MockWebServer
    // подключены (Этап 2, срезы 1–2).
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/detekt.yml"))
}

// Jacoco: покрытие domain/util — критерий приёмки Этапа 3 (docs/05_roadmap.md, ≥ 80%).
// Запуск: ./gradlew jacocoDomainReport → app/build/reports/jacoco/jacocoDomainReport

tasks.withType<Test> {
    extensions.configure<JacocoTaskExtension> {
        isEnabled = true
    }
}

tasks.register<JacocoReport>("jacocoDomainReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("intermediates/classes/debug/transformDebugClassesWithAsm/dirs")),
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(file(layout.buildDirectory.dir("jacoco/testDebugUnitTest.exec")))
}
