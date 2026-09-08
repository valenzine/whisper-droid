import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}
fun signingValue(propertyName: String, environmentName: String): String? =
    keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
        ?: System.getenv(environmentName)?.takeIf { it.isNotBlank() }
val releaseSigningConfigured = listOf(
    signingValue("storeFile", "RELEASE_STORE_FILE"),
    signingValue("storePassword", "RELEASE_STORE_PASSWORD"),
    signingValue("keyAlias", "RELEASE_KEY_ALIAS"),
    signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
).all { it != null }
val releaseStoreFile = signingValue("storeFile", "RELEASE_STORE_FILE")
val releaseStore = releaseStoreFile?.let(rootProject::file)
val releaseArtifactRequested = gradle.startParameter.taskNames.any { requestedTask ->
    val taskName = requestedTask.substringAfterLast(':')
    taskName in setOf("build", "assemble") ||
        (taskName.contains("Release") && listOf("assemble", "bundle", "package", "install", "publish").any(taskName::startsWith))
}
if (releaseArtifactRequested && !releaseSigningConfigured) {
    throw GradleException(
        "Release signing is not configured. Copy keystore.properties.example to " +
            "keystore.properties and provide a durable upload keystore."
    )
}
if (releaseArtifactRequested && (releaseStore?.isFile != true || !releaseStore.canRead())) {
    throw GradleException(
        "Release keystore is missing or unreadable: ${releaseStore?.absolutePath ?: "<not configured>"}"
    )
}

android {
    namespace = "com.valenzine.whisperdroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.valenzine.whisperdroid"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = requireNotNull(releaseStore)
                    storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
                    keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                    keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
                }
            }
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
