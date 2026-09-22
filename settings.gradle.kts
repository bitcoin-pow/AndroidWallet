pluginManagement {
    repositories {
        maven { url = uri("vendor/maven") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("vendor/maven") }
    }
}

rootProject.name = "BTCW"
include(":app")
