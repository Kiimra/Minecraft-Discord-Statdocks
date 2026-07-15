plugins {
    java
}

group = "dev.kiimra"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// By default the plugin compiles against the bundled API stubs (src/stubs),
// which mirror the exact Bukkit/Paper API surface this plugin uses and are
// never packaged into the jar. Build with -PusePaperApi=true to compile
// against the real Paper API instead (recommended before publishing a
// release; requires network access to repo.papermc.io).
val usePaperApi = providers.gradleProperty("usePaperApi").getOrElse("false").toBoolean()

repositories {
    mavenCentral()
    if (usePaperApi) {
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

sourceSets {
    create("stubs")
}

dependencies {
    if (usePaperApi) {
        compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paperApiVersion").get()}")
    } else {
        compileOnly(sourceSets["stubs"].output)
    }
    // Bundled by the Paper server at runtime; used for JSON on the Discord side.
    compileOnly("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("DiscordStatdockUpdater")
    // The stubs live in a separate compile-only source set, so they are never
    // part of the main output that goes into the jar.
}

tasks.test {
    useJUnitPlatform()
}
