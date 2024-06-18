// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    val androidVersion = "8.3.1"
    val kotlinVersion = "2.0.0"

    id("com.android.application") version androidVersion apply false
    id("com.android.library") version androidVersion apply false
    id("org.jetbrains.kotlin.android") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
}
