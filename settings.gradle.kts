rootProject.name = "loadmin"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "loadmin-core",
    "loadmin-ui",
    "loadmin-spring-boot-starter",
    "loadmin-demo",
)
