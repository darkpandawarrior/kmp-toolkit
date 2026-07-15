pluginManagement {
    // Shared convention plugins (shared.kmp.library / shared.kmp.compose / shared.android.library /
    // shared.test / …), the same repo the consumer apps (PaymentsLab/Mileway/HireSignal) vendor at
    // external/kmp-build-logic. Sibling checkout on disk here — apps that vendor kmp-toolkit as
    // external/kmp-toolkit also vendor kmp-build-logic as its sibling external/kmp-build-logic, so
    // this relative path resolves the same way in both the standalone repo and the vendored copy.
    includeBuild("../kmp-build-logic")
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
        // Cashfree nextgen SDK (com.cashfree.pg:api / :ui) is published only to Cashfree's own repo.
        maven {
            url = uri("https://maven.cashfree.com/release")
            content { includeGroup("com.cashfree.pg") }
        }
        // Square In-App Payments SDK (com.squareup.sdk.in-app-payments:*) is published only here, not
        // Maven Central. com.squareup.android:truststore/socket-factory are transitive deps also
        // hosted only here (card-entry's own .pom omits them).
        maven {
            url = uri("https://sdk.squareup.com/public/android")
            content {
                includeGroup("com.squareup.sdk.in-app-payments")
                includeGroup("com.squareup.android")
            }
        }
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
include(":llm-chat")
include(":feedback")
include(":location")
include(":app-shell")
include(":payments-api")
include(":offline-outbox")
include(":bots-policy")
include(":provider:stripe")
include(":provider:upi-intent")
include(":provider:cashfree")
include(":provider:googlepay")
include(":provider:omise")
include(":provider:razorpay")
include(":provider:square")
include(":provider:hosted-webview")
include(":provider:flutterwave")
include(":provider:paystack")
include(":provider:paytm")
include(":provider:stripe-connect")
include(":provider:cash")
include(":provider:nmi")
include(":provider:peach")
include(":provider:mobile-money")
include(":provider:mpesa")
include(":provider:wallet")
include(":provider:xendit")
