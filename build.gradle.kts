// database-diagram-jetbrains/build.gradle.kts

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.1.20"
  id("org.jetbrains.intellij.platform") version "2.10.2"
  id("com.github.node-gradle.node") version "7.1.0"
}

group = "com.abugyda"
version = "1.0-SNAPSHOT"

// ---------------------------------------------------------------------------
// Web project location — resolved in priority order:
//   1. WEB_PROJECT_DIR environment variable   (ideal for CI)
//   2. webProjectDir property in gradle.properties  (ideal for local dev)
//   3. Sibling directory fallback "../database-diagram-web"
// ---------------------------------------------------------------------------
val webProjectDir: File = run {
  val raw = System.getenv("WEB_PROJECT_DIR")
    ?: (findProperty("webProjectDir") as String?)
    ?: "../database-diagram-web"
  file(raw).canonicalFile
}

require(webProjectDir.exists()) {
  """
    Web project not found at: $webProjectDir
 
    Fix one of:
      a) Set webProjectDir=<path> in gradle.properties (keep machine-specific paths local)
      b) Export WEB_PROJECT_DIR=<path> in your shell / CI environment
      c) Place the web project as a sibling: ../database-diagram-web
    """.trimIndent()
}

val webDistDir: File = webProjectDir.resolve("dist")
val webResourcesDir = layout.buildDirectory.dir("generated/webResources")


// ---------------------------------------------------------------------------
// Node / npm configuration
// ---------------------------------------------------------------------------
node {
  version.set("22.22.0")
  download.set(true)
  workDir.set(file("${project.projectDir}/.gradle/nodejs"))
  nodeProjectDir.set(webProjectDir)
}


// ---------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------
tasks {

  withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
  }

  named("npmInstall") {
    inputs.file(webProjectDir.resolve("package.json"))
    inputs.file(webProjectDir.resolve("package-lock.json"))
  }

  // 1. Run `npm run build` in the standalone web project.
  val buildWebview by registering(com.github.gradle.node.npm.task.NpmTask::class) {
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))

    inputs.dir(webProjectDir.resolve("src"))
    inputs.dir(webProjectDir.resolve("public"))
    inputs.file(webProjectDir.resolve("package.json"))
    inputs.file(webProjectDir.resolve("package-lock.json"))
    inputs.file(webProjectDir.resolve("vite.config.ts"))
    inputs.file(webProjectDir.resolve("index.html"))
    inputs.files(
      webProjectDir.resolve("tsconfig.json"),
      webProjectDir.resolve("tsconfig.app.json"),
      webProjectDir.resolve("tsconfig.node.json")
    )
    inputs.dir(webProjectDir.resolve("tests"))
    outputs.dir(webDistDir)
  }

  // Keep generated assets out of src; Sync also removes stale web chunks.
  val copyWebDist by registering(Sync::class) {
    dependsOn(buildWebview)
    from(webDistDir)
    into(webResourcesDir.map { it.dir("web") })
  }

  processResources {
    dependsOn(copyWebDist)
    from(webResourcesDir)
  }
}


// ---------------------------------------------------------------------------
// Repositories & dependencies (unchanged from original)
// ---------------------------------------------------------------------------

repositories {
  mavenCentral()
  intellijPlatform { defaultRepositories() }
}

val localIdePath = "C:\\Program Files\\JetBrains\\IntelliJ IDEA 2025.3.3"

dependencies {
  intellijPlatform {
    //intellijIdea("2025.2.4")

    // Use your local IDE installation instead of downloading one
    local(localIdePath)

    // keep the testFramework declaration here if you need it
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

    // Add JetBrains Database and YAML plugins
    // 1. Add YAML plugin for parsing and updating the .erd.yaml layout file
    bundledPlugin("org.jetbrains.plugins.yaml")
    bundledPlugin("com.intellij.modules.json")

    // 2. Add Database plugin for parsing SQL files (PSI)
    bundledPlugin("com.intellij.database") // SQL PSI comes with this
  }

  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
  testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
  sandboxContainer.set(
    layout.buildDirectory.dir(
      providers.gradleProperty("diagramSandbox").orElse("idea-sandbox")
    )
  )
  pluginConfiguration {
    ideaVersion { sinceBuild = "252.25557" }
    changeNotes = "Initial version".trimIndent()
  }
}

