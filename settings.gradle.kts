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
    repositories {
        google()
        mavenCentral()
        // 이렇게 수정해주세요
        maven(url = uri("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/"))
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
    }
    }



rootProject.name = "Police-and-thief"
include(":app")
