plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.0"
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
        // basic
        implementation("dev.neovoxel.jarflow:JarFlow:2.0.0")
        compileOnly("org.java-websocket:Java-WebSocket:1.6.0")
        compileOnly("dev.neovoxel.nbapi:NeoBotAPI:1.2.3") {
            exclude("org.java-websocket")
        }
        // implementation("io.github.classgraph:classgraph:4.8.184")
        compileOnly("org.json:json:20250517")
        compileOnly("org.slf4j:slf4j-api:2.0.17")
        compileOnly("org.graalvm.js:js:22.0.0.2")

        // plugins
        implementation("org.pf4j:pf4j:3.6.0")

        // storage
        compileOnly("dev.neovoxel.nsapi:NeoStorageAPI:1.1.0")
        compileOnly("com.zaxxer:HikariCP:4.0.3")
        compileOnly("com.mysql:mysql-connector-j:8.2.0")
        compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.6")
        compileOnly("org.postgresql:postgresql:42.7.8")
        compileOnly("com.h2database:h2:2.2.224")
        compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")

        // annotations
        compileOnly("org.projectlombok:lombok:1.18.42")
        annotationProcessor("org.projectlombok:lombok:1.18.42")
        compileOnly("org.jetbrains:annotations:24.0.1")
        annotationProcessor("org.jetbrains:annotations:24.0.1")

        // for migration
        compileOnly("org.yaml:snakeyaml:2.5")
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.jar {
        archiveFileName.set("NeoBot-${archiveFileName.get()}")
    }

    tasks.shadowJar {
        archiveFileName.set("NeoBot-${archiveFileName.get()}")
        relocate("org.bstats", "dev.neovoxel.neobot.libs.bstats")
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