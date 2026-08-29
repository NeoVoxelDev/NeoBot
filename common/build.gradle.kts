dependencies {
    testImplementation("dev.neovoxel.nbapi:NeoBotAPI:1.3.0")
    testImplementation("org.json:json:20250517")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
