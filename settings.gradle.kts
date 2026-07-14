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
    // PREFER_SETTINGS: the Kotlin/Wasm toolchain plugins add Node/Yarn/Binaryen distribution repos at
    // project level for the wasmJs() targets (result/common/mvi-core/feedback/designsystem);
    // FAIL_ON_PROJECT_REPOS would reject those.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Node.js / Yarn / Binaryen distributions for the Kotlin/Wasm toolchain (wasmJs browser()).
        ivy {
            name = "Node.js Distributions"
            url = uri("https://nodejs.org/dist")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy {
            name = "Yarn Distributions"
            url = uri("https://github.com/yarnpkg/yarn/releases/download")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy {
            name = "Binaryen Distributions"
            url = uri("https://github.com/WebAssembly/binaryen/releases/download")
            patternLayout { artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "kmp-toolkit"

// One monorepo, natural module paths — no ":lib" collision workaround needed (that only existed
// because the modules used to be separate included builds). Each module publishes com.siddharth.kmp:<name>.
include(":result")
include(":common")
include(":mvi-core")
include(":network")
include(":security")
include(":designsystem")
include(":ai")
include(":feedback")
