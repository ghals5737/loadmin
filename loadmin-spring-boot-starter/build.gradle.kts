plugins {
    `java-library`
}

dependencies {
    api(project(":loadmin-core"))
    runtimeOnly(project(":loadmin-ui"))

    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("com.fasterxml.jackson.core:jackson-databind")

    // The load engine needs these at runtime in the consuming app.
    implementation(platform(libs.spring.boot.dependencies))
    implementation("org.springframework:spring-webflux")
    implementation("io.projectreactor.netty:reactor-netty-http")
    implementation("io.micrometer:micrometer-core")
}
