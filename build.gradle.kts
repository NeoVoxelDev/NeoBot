plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.0"
}

val graalModern by configurations.creating

repositories {
    mavenCentral()
}

dependencies {
    graalModern("org.graalvm.polyglot:js:25.0.0")
    graalModern("org.graalvm.polyglot:polyglot:25.0.0")
}

group = properties["group"]!!
version = properties["version"]!!

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.gradleup.shadow")

    group = properties["group"]!!
    version = properties["version"]!!

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        // Runtime libraries are bundled into each platform jar. Keeping these
        // here avoids network access and class-loader mutation during startup.
        implementation("org.java-websocket:Java-WebSocket:1.6.0")
        implementation("dev.neovoxel.nbapi:NeoBotAPI:1.3.0") {
            exclude(group = "org.java-websocket")
            exclude(group = "org.apache.httpcomponents")
            exclude(group = "commons-codec")
            exclude(group = "commons-logging")
            exclude(group = "org.json")
            exclude(group = "org.slf4j")
        }
        implementation("io.github.classgraph:classgraph:4.8.184")
        implementation("org.json:json:20250517")
        implementation("org.apache.httpcomponents:httpclient:4.5.14")
        implementation("org.apache.httpcomponents:httpcore:4.4.16")
        implementation("commons-codec:commons-codec:1.15")
        implementation("commons-logging:commons-logging:1.2")
        implementation("com.zaxxer:HikariCP:4.0.3")
        implementation("org.graalvm.js:js:22.0.0.2")
        compileOnly("org.slf4j:slf4j-api:2.0.17")

        // storage
        implementation("dev.neovoxel.nsapi:NeoStorageAPI:1.1.0")
        implementation("com.mysql:mysql-connector-j:8.2.0")
        implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
        implementation("org.postgresql:postgresql:42.7.8")
        implementation("com.h2database:h2:2.2.224")
        implementation("org.xerial:sqlite-jdbc:3.50.3.0")

        // annotations
        compileOnly("org.projectlombok:lombok:1.18.42")
        annotationProcessor("org.projectlombok:lombok:1.18.42")
        compileOnly("org.jetbrains:annotations:24.0.1")
        annotationProcessor("org.jetbrains:annotations:24.0.1")

        // for migration
        implementation("org.yaml:snakeyaml:2.5")
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.jar {
        archiveFileName.set("NeoBot-${archiveFileName.get()}")
    }

    tasks.shadowJar {
        archiveFileName.set("NeoBot-${archiveFileName.get()}")
        relocate("org.bstats", "dev.neovoxel.neobot.libs.bstats")
        mergeServiceFiles()
        manifest.attributes["Multi-Release"] = "true"
        into("META-INF/versions/17") {
            from(graalModern.filter { it.extension == "jar" }.map { zipTree(it) })
            exclude("META-INF/services/**")
            exclude("META-INF/versions/**")
            exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
            exclude("META-INF/MANIFEST.MF")
            exclude("module-info.class")
        }
        into("META-INF/versions/9") {
            from(graalModern.filter { it.extension == "jar" }.map { zipTree(it) }) {
                include("META-INF/versions/9/**")
                eachFile { path = path.removePrefix("META-INF/versions/9/") }
                includeEmptyDirs = false
            }
        }
        into("META-INF/versions/21") {
            from(graalModern.filter { it.extension == "jar" }.map { zipTree(it) }) {
                include("META-INF/versions/21/**")
                eachFile { path = path.removePrefix("META-INF/versions/21/") }
                includeEmptyDirs = false
            }
        }
        from(graalModern.filter { it.extension == "jar" }.map { zipTree(it) }) {
            include("META-INF/graalvm/**")
        }
        // Graal resolves native attach support from this root resource path.
        from(graalModern.filter { it.extension == "jar" }.map { zipTree(it) }) {
            include("META-INF/resources/engine/libtruffleattach/**")
        }
    }
}

tasks.register("package") {
    val outputDir = rootDir.resolve("outputs")
    outputDir.mkdirs()
    subprojects.forEach {
        if (it.project.name == "common" || it.project.name == "fabric") {
            return@forEach
        }

        if (it.tasks.map { it.name }.contains("shadowJar")) {
            dependsOn(it.tasks.named("shadowJar"))
            doLast {
                val file = it.tasks.getByName<AbstractArchiveTask>("shadowJar").archiveFile.get().asFile
                file.copyTo(outputDir.resolve(file.name), true)
            }
        } else if (it.tasks.map { it.name }.contains("remapJar")) {
            dependsOn(it.tasks.named("remapJar"))
            doLast {
                val file = it.tasks.getByName<AbstractArchiveTask>("remapJar").archiveFile.get().asFile
                file.copyTo(outputDir.resolve(file.name), true)
            }
        } else {
            dependsOn(it.tasks.named("jar"))
            doLast {
                val file = it.tasks.getByName<Jar>("jar").archiveFile.get().asFile
                file.copyTo(outputDir.resolve(file.name), true)
            }
        }
    }
}

tasks.clean {
    delete(rootDir.resolve("outputs"))
}

tasks.build {
    dependsOn("package")
}
