plugins {
    id("java")
}

group = properties["group"]!!
version = properties["version"]!!

subprojects {
    apply(plugin = "java")

    group = properties["group"]!!
    version = properties["version"]!!

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        // basic
        implementation("dev.neovoxel.jarflow:JarFlow:1.1.0")
        compileOnly("dev.neovoxel.nbapi:NeoBotAPI:1.1.2")
        compileOnly("org.json:json:20250517")
        compileOnly("org.slf4j:slf4j-api:2.0.17")
        compileOnly("org.graalvm.js:js:22.0.0.2")

        // storage
        compileOnly("dev.neovoxel.nsapi:NeoStorageAPI:1.0.0")
        compileOnly("com.zaxxer:HikariCP:4.0.3")
        compileOnly("com.mysql:mysql-connector-j:8.2.0")
        compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.6")
        compileOnly("org.postgresql:postgresql:42.7.8")
        compileOnly("com.h2database:h2:2.4.240")
        compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")

        // annotations
        compileOnly("org.projectlombok:lombok:1.18.42")
        annotationProcessor("org.projectlombok:lombok:1.18.42")
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}