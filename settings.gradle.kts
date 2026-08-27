pluginManagement {
    repositories {
        // Google Home SDK is distributed through a developer-only local Maven
        // package rather than the public Google Maven repository.
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Google Home APIs are supplied as a private local Maven package.
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "Minova Cinema"
include(":app")
