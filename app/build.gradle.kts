plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
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
        compileSdk = 35

        // App Versioning
        versionCode = 230
        versionName = "v2.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
                        val adjustedDeviceName = thisDevice.lowercase().replace(" ", "")
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
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(libs.android.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.preference.ktx)

    // Compose
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.viewbinding)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.unit)

    // Logging
    implementation(libs.timber)

    // Root helper library
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)

    // Navigation
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.transitions)
    implementation(libs.voyager.core)

    // Android Studio Preview support
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Unit Testing
    testImplementation(libs.junit)

    // Instrumented Testing
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.tracing.ktx)
}
