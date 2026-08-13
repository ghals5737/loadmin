plugins {
    `java-library`
}

dependencies {
    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly("org.springframework:spring-webmvc")
}
