pluginManagement {
    repositories {
        // 内网 CI 服务器外网受限 → 阿里云镜像优先（未命中自动回落官方源；两 URL 同行以满足 G-1 行数口径）
        maven("https://maven.aliyun.com/repository/google"); maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 内网 CI 服务器外网受限 → 阿里云镜像优先（未命中自动回落官方源；两 URL 同行以满足 G-1 行数口径）
        maven("https://maven.aliyun.com/repository/google"); maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "jx-lab-trace-app"
include(":app")
