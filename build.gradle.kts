// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.3.0" apply false // Use the latest stable version
    id("com.android.library") version "8.3.0" apply false // Use the latest stable version
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false // Use the same Kotlin version as in app/build.gradle.kts
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}