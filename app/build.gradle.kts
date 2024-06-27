import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "me.arianb.usb_hid_client"
    defaultConfig {
        applicationId = "me.arianb.usb_hid_client"

        // Android SDK Build Tools version
        buildToolsVersion = "34.0.0"

        // SDK support
        minSdk = 26
        targetSdk = 33
        compileSdk = 34

        // App Versioning
        versionCode = 230
        versionName = "v2.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Build configuration
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs.useLegacyPackaging = true
    }
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }
    composeCompiler {
        enableStrongSkippingMode = true
    }

    // Disable Google-encrypted binary blobs
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    // Java version
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkAllWarnings = true

        baseline = file("lint-baseline.xml")

        // I used to have this disabled, but I shouldn't. If an error is unjustified, then I should knock its severity
        // down to "warning" or disable it. Appropriate lint errors should cause a failure.
        //abortOnError = false
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        val minSDK = android.defaultConfig.minSdk!!
        val targetSDK = android.defaultConfig.targetSdk!!
        managedDevices {
            animationsDisabled = true
            localDevices {
                val testDevices = arrayOf("Pixel 5")
                val apiLevelsMissingATD = setOf(26, 27, 28, 29, 34)
                val testPrimarySystemImage = "aosp-atd"
                val testFallbackSystemImage = "aosp"
                for (thisDevice in testDevices) {
                    for (thisApiLevel in minSDK..targetSDK) {
                        // lowercase and remove spaces
                        val adjustedDeviceName = thisDevice.lowercase(Locale.US).replace(" ", "")
                        val name = "${adjustedDeviceName}_${thisApiLevel}"
                        create(name) {
                            device = thisDevice
                            apiLevel = thisApiLevel
                            // Some API levels don't provide my preferred system image, so use the fallback in those cases
                            systemImageSource = if (thisApiLevel in apiLevelsMissingATD) {
                                testFallbackSystemImage
                            } else {
                                testPrimarySystemImage
                            }
                        }
                    }
                }
            }
            groups {
                // NOTE: Testing too many API levels in CI causes it to run out of storage, I could work
                //       around it, but that's something for later.
                create("ci") {
                    val apiLevelsToTestInCI = arrayOf(29, 30, 32, targetSDK)
                    for (device in localDevices) {
                        if (device.apiLevel in apiLevelsToTestInCI) {
                            targetDevices.add(device)
                        }
                    }
                }
            }
        }
    }
}

dependencies {
    // AndroidX
    val androidXAppCompatVersion = "1.7.0"
    val androidXConstraintLayoutVersion = "2.1.4"
    val androidXCoreVersion = "1.13.1"
    val androidXLifecycleVersion = "2.8.2"
    val androidXPreferenceVersion = "1.2.1"
    val androidXTestJunit = "1.2.0"

    // Compose
    val composeActivityVersion = "1.9.0"
    val composeMaterial3Version = "1.2.1"
    val composeUIVersion = "1.6.8"
    val composeBomVersion = "2024.06.00"

    // Other libraries
    val androidMaterialVersion = "1.12.0"
    val junitVersion = "4.13.2"
    val libsuVersion = "5.3.0"
    val timberVersion = "5.0.1"
    val voyagerVersion = "1.1.0-beta02"

    implementation("com.google.android.material:material:$androidMaterialVersion")
    implementation("androidx.appcompat:appcompat:$androidXAppCompatVersion")
    implementation("androidx.constraintlayout:constraintlayout:$androidXConstraintLayoutVersion")
    implementation("androidx.core:core-ktx:$androidXCoreVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$androidXLifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$androidXLifecycleVersion")
    implementation("androidx.preference:preference-ktx:$androidXPreferenceVersion")

    // Misc 3rd party
    implementation("com.jakewharton.timber:timber:$timberVersion")
    implementation("com.github.topjohnwu.libsu:core:$libsuVersion")

    // Compose
    implementation("androidx.activity:activity-compose:$composeActivityVersion")
    implementation("androidx.compose.material3:material3:$composeMaterial3Version")
    implementation("androidx.compose.ui:ui:$composeUIVersion")
    implementation("androidx.compose.ui:ui-graphics:$composeUIVersion")
    implementation("androidx.compose.ui:ui-viewbinding:$composeUIVersion")
    implementation("androidx.compose.animation:animation-core:$composeUIVersion")
    implementation("androidx.compose.foundation:foundation-layout:$composeUIVersion")
    implementation("androidx.compose.foundation:foundation:$composeUIVersion")
    implementation("androidx.compose.material:material-icons-core:$composeUIVersion")
    implementation("androidx.compose.runtime:runtime:$composeUIVersion")
    implementation("androidx.compose.ui:ui-text:$composeUIVersion")
    implementation("androidx.compose.ui:ui-unit:$composeUIVersion")

    // Navigation
    implementation("cafe.adriel.voyager:voyager-navigator:$voyagerVersion")
    implementation("cafe.adriel.voyager:voyager-transitions:$voyagerVersion")
    implementation("cafe.adriel.voyager:voyager-core:$voyagerVersion")

    // Android Studio Preview support
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit Testing
    testImplementation("junit:junit:$junitVersion")

    // Instrumented Testing
    androidTestImplementation("junit:junit:$junitVersion")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeUIVersion")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$composeUIVersion")
    androidTestImplementation("androidx.test.ext:junit:$androidXTestJunit")
}
