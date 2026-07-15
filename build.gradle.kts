plugins {
    java
}

group = "dev.kiimra"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
  maven {
    name = "papermc"
    url = uri("https://repo.papermc.io/repository/maven-public/")
  }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

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
