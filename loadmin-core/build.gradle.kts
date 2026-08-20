plugins {
    `java-library`
}

dependencies {
    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework:spring-webflux")
    compileOnly("io.projectreactor.netty:reactor-netty-http")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("com.fasterxml.jackson.core:jackson-databind")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
