pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo.maven.apache.org/maven2/") }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo.maven.apache.org/maven2/") }
    }
}

rootProject.name = "ComicRack-Android"
include(":app")
//include(":remote-library") // this is a planned module to enable reading comics from a Google Drive location
