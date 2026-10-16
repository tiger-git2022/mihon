plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)
    alias(mihonx.plugins.spotless)
}

android {
    namespace = "mihon.presentation.glass"
}

dependencies {
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // Backdrop is part of the API used by future glass components and their content sources.
    api(libs.backdrop)
}
