plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// versionName = 提交短号（CI 注入 GITHUB_SHA）；versionCode = CI 运行号（递增整数）
val gitSha: String = (System.getenv("GITHUB_SHA") ?: "local00").take(7)
val buildNumber: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1
// 服务端基址（真机联调用环境变量 JX_BASE_URL 覆盖；默认取验收环境域名）
val baseUrl: String = (System.getenv("JX_BASE_URL") ?: "http://office.hunanyichu.com:5502").trimEnd('/')
// 飞书开放平台 app_id（公开标识符；真机联调用 JX_FEISHU_CLIENT_ID 覆盖，与 BASE_URL 同法）。
// 业务代码只经 BuildConfig 引用，不出现字面量（A-5 修订口径）。
val feishuClientId: String = (System.getenv("JX_FEISHU_CLIENT_ID") ?: "cli_aa40b8435439dd0c").trim()

android {
    namespace = "cn.hunanjiangxi.jxlabtrace"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.hunanjiangxi.jxlabtrace"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = gitSha
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "FEISHU_CLIENT_ID", "\"$feishuClientId\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // 统一签名（飞书移动应用登录的 MD5 绑定）：debug/release 同一证书，
    // keystore 已强制入库（gitignore 豁免，见 MIMO-TASK-LARKSSO 回执 §签名）。
    signingConfigs {
        create("jxlabtrace") {
            storeFile = file("jx-lab-trace.keystore")
            storePassword = "9a057608abc94c2e4284"
            keyAlias = "jxlabtrace"
            keyPassword = "9a057608abc94c2e4284"
            // AGP 8 默认只出 v2+ 签名块，`keytool -printcert -jarfile` 只认 v1（META-INF）——
            // 显式开 v1 以保证 CI 侧该命令可读出证书指纹（校验口径）。
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("jxlabtrace")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("jxlabtrace")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    testImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // LarkSSO 官方 SDK（飞书 SDK Guide「下载 LarkSSO Android lib」→ LarkSSOSDKAndroid-3.0.10.zip，
    // 解出 aar 入库 libs/）。不用 flatDir：settings.gradle.kts 为 FAIL_ON_PROJECT_REPOS，
    // 工程级仓库声明会被拒绝；files() 本地文件依赖无需仓库（S-1 编译实证口径）。
    implementation(files("libs/larksso-3.0.10.aar"))
    // SDK 的 LarkSSOActivity 继承 AppCompatActivity、主题引用 Theme.AppCompat（反编译 aar 实证），
    // 必须运行时依赖；文档另列的 com.githang:status-bar-compat 反编译零引用 ⇒ 不引入（回执注明）。
    implementation("androidx.appcompat:appcompat:1.7.0")

    // B2 白名单新增（MIMO-NEXT-BATCH-02 §2）：相机三件套 + ML Kit 条码识别
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // 测试配套（白名单外，理由见 MIMO-NEXT-BATCH-01 回执「依赖变更」）：
    // mockwebserver = okhttp 官方测试件；robolectric = JVM 单测跑 Compose 布局断言所需
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped", "standardOut", "standardError")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
