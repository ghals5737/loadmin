plugins {
    `java-library`
}

dependencies {
    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework:spring-webflux")
    compileOnly("io.projectreactor.netty:reactor-netty-http")
    compileOnly("io.micrometer:micrometer-core")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
