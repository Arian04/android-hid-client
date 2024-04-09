import java.util.Locale

plugins {
    id("com.android.application")
}

android {
    compileSdk = 34

    defaultConfig {
        applicationId = "me.arianb.usb_hid_client"
        minSdk = 26
        targetSdk = 33
        versionCode = 5
        versionName = "v2.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    @Suppress("UnstableApiUsage")
    buildTypes {
        getByName("release") {
            // Minify, but do not obfuscate.
            // Disabling obfuscation since it's open source anyway, so I don't know of any reason to obfuscate the code.
            postprocessing {
                isRemoveUnusedCode = true
                isRemoveUnusedResources = true
                isOptimizeCode = true
                isObfuscate = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "me.arianb.usb_hid_client"
    packaging.jniLibs.useLegacyPackaging = true
    buildFeatures.buildConfig = true

    // Disable Google-encrypted binary blobs
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    lint.abortOnError = false

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
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference:1.2.1")
    //noinspection GradleDependency: Locked to 4.7.1 because of issue #484
    implementation("com.jakewharton.timber:timber:4.7.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
