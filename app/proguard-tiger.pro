# Fork (tiger-git2022/mihon): rules for MSAL (Microsoft sign-in used by OneDrive library sync).
# MSAL ships its own consumer rules; these cover what is left.

# display-mask (Surface Duo hinge support) is excluded in build.gradle.kts
-dontwarn com.microsoft.device.display.**

# MSAL and its "common" libraries deserialize configuration and tokens with Gson/reflection
-keep class com.microsoft.identity.** { *; }
-keep interface com.microsoft.identity.** { *; }
-keep class com.nimbusds.** { *; }
-dontwarn com.microsoft.identity.**
-dontwarn com.nimbusds.**

# Compile-time only annotations and optional dependencies referenced by MSAL
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn lombok.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-dontwarn io.opentelemetry.**
-dontwarn com.google.crypto.tink.**
-dontwarn net.jcip.annotations.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
