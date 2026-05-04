plugins {
    id("org.springframework.boot")
    id("java")
}

dependencies {
    implementation(project(":myclaw-core"))
    implementation(project(":myclaw-ai"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.projectreactor:reactor-core")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}
