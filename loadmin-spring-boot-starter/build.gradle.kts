plugins {
    `java-library`
}

dependencies {
    api(project(":loadmin-core"))
    runtimeOnly(project(":loadmin-ui"))

    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-webmvc")
}
