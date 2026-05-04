plugins {
    `java-library`
}

dependencies {
    api(project(":myclaw-core"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-webflux")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.projectreactor:reactor-core")
    implementation("org.slf4j:slf4j-api")
}
