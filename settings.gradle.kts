pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("mihonx") {
            from(files("gradle/mihon.versions.toml"))
        }
    }

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Mihon"
include(":app")
include(":baseline-profile")
include(":core-metadata")
include(":core:archive")
include(":core:common")
include(":core:metro")
include(":data")
include(":domain")
include(":i18n")
include(":icons:custom-material-symbols")
include(":icons:material-symbols")
include(":icons:simple-icons")
include(":presentation-core")
include(":presentation-widget")
include(":source-api")
include(":source-local")
include(":telemetry")

// region Fork (tiger-git2022/mihon): local-only build output redirect
// Keeps generated files outside OneDrive so cloud placeholders and sync locks cannot corrupt
// Gradle's incremental compilation snapshots. Off on CI (`CI` env var) or with -Ptiger.localBuildDir=false.
val tigerLocalBuildDir = providers.gradleProperty("tiger.localBuildDir").orNull?.toBoolean()
    ?: (providers.environmentVariable("CI").orNull == null)
if (tigerLocalBuildDir) {
    val localBuildRoot = File(System.getProperty("user.home"), ".gradle/project-builds/mihon")
    gradle.lifecycle.beforeProject {
        val projectBuildPath = if (path == ":") "root" else path.removePrefix(":").replace(":", "/")
        layout.buildDirectory.set(localBuildRoot.resolve(projectBuildPath))
    }
}
// endregion
