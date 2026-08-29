repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    // Hosts historical transitive artifacts referenced by the 1.8.8 API.
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    // Compile against the oldest supported Bukkit API. Newer server features
    // are detected at runtime so one jar can run from 1.8.8 onwards.
    val bukkitApiVersion = providers.gradleProperty("bukkitApiVersion")
        .getOrElse("1.8.8-R0.1-SNAPSHOT")
    compileOnly("org.spigotmc:spigot-api:$bukkitApiVersion")
    compileOnly("me.clip:placeholderapi:2.11.7")
    implementation(project(":common"))

    testImplementation("org.spigotmc:spigot-api:$bukkitApiVersion")
    testImplementation("junit:junit:4.13.2")
    // Real Log4j2 classes for BukkitConsoleSenderLogCaptureTest, which exercises the reflection-based
    // LogCapture appender against the actual LogManager/Logger/Appender machinery Paper bundles at runtime.
    testImplementation("org.apache.logging.log4j:log4j-core:2.26.0")
}

tasks.shadowJar {
    // GraalJS uses Java 11+ overlays to avoid JDK internals removed by modern
    // Java releases. Java 8 ignores these entries in a multi-release jar.
    exclude("META-INF/versions/**/module-info.class")
    manifest.attributes["Multi-Release"] = "true"
}
