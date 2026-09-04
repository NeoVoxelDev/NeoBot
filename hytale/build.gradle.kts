repositories {
    maven ("https://nexus.lucko.me/repository/maven-hytale/")
}

dependencies {
    implementation(project(":common"))
    // hytale
    compileOnly("com.hypixel.hytale:HytaleServer:2026.01.13-dcad8778f-SNAPSHOT")
}
