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

    // The load engine needs these at runtime in the consuming app. They carry
    // explicit versions instead of coming from the Boot BOM: importing that BOM
    // into the published POM pushes its versions onto the consuming
    // application, which silently upgrades part of its dependency graph — an
    // application on Boot 3.4 ended up with io.prometheus classes from 3.5 and
    // failed to start. A version here is only a floor; the application's own
    // dependency management wins.
    implementation(libs.spring.webflux)
    implementation(libs.reactor.netty.http)
    implementation(libs.micrometer.core)
}
