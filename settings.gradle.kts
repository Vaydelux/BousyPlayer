pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "BousyPlayer"
include(":app", ":session-smoke", ":native-smoke")
project(":session-smoke").projectDir = file("tools/gradle-session")
project(":native-smoke").projectDir = file("tools/gradle-native")
