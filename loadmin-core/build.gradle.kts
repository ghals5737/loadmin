plugins {
    `java-library`
}

dependencies {
    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework:spring-webflux")
    compileOnly("io.projectreactor.netty:reactor-netty-http")
    compileOnly("io.micrometer:micrometer-core")
}
