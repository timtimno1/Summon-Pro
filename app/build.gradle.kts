plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val mapsApiKey = providers.gradleProperty("mapsApiKey")
    .orElse(providers.environmentVariable("MAPS_API_KEY"))
    .orElse("DEFAULT_API_KEY")

android {
    namespace = "com.justjdupuis.summonpro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.justjdupuis.summonpro"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey.get()
        buildConfigField(
            "String",
            "FLEET_API_BASE_URL",
            "\"${providers.gradleProperty("fleetApiBaseUrl").orElse("https://fleet-api.prd.na.vn.cloud.tesla.com/").get()}\""
        )
    }

    buildFeatures {
        buildConfig = true
        // ...
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.2")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.2")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.preference:preference-ktx:1.2.1") // or latest

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")


    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.maps.android:android-maps-utils:2.3.0")
}

val validateMapsApiKey by tasks.registering {
    doLast {
        check(mapsApiKey.get().isNotBlank() && mapsApiKey.get() != "DEFAULT_API_KEY") {
            "A Google Maps API key is required. Set mapsApiKey in ~/.gradle/gradle.properties " +
                "or export MAPS_API_KEY before building."
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(validateMapsApiKey)
}
