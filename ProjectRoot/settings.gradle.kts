pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Lifesaiver"
include(":lifesaiver", ":rescuer", ":shared")

project(":lifesaiver").projectDir = file("Lifesaiver")
project(":rescuer").projectDir = file("Rescuer")
project(":shared").projectDir = file("shared")
 
