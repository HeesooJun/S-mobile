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

rootProject.name = "Lifesaivior"
include(":lifesaivior", ":rescuer", ":shared")

project(":lifesaivior").projectDir = file("Lifesaivior")
project(":rescuer").projectDir = file("Rescuer")
project(":shared").projectDir = file("shared")
 
