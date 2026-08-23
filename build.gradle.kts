import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    group = "io.github.ghals5737"
    version = "0.1.0"
}

/** Published to Maven Central; loadmin-demo is a local sample and stays out. */
val publishedModules = setOf("loadmin-core", "loadmin-ui", "loadmin-spring-boot-starter")

val moduleDescriptions = mapOf(
    "loadmin-core" to "loadmin core: @LoadTest annotation, endpoint scanner, load engine, "
            + "request templates and run history",
    "loadmin-ui" to "loadmin web UI resources served at /loadmin",
    "loadmin-spring-boot-starter" to "Spring Boot starter that exposes an annotation-driven "
            + "load testing UI at /loadmin",
)

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    if (name !in publishedModules) {
        return@subprojects
    }

    apply(plugin = "com.vanniktech.maven.publish")

    extensions.configure<MavenPublishBaseExtension> {
        // Uploads a signed bundle and waits for validation. The release itself
        // is confirmed in the Central Portal — a published version can never be
        // changed or removed, so that button stays human. Pass true here to
        // release automatically once validation passes.
        publishToMavenCentral(automaticRelease = false)

        // Central requires signatures, but a contributor without a GPG key must
        // still be able to run publishToMavenLocal.
        val hasSigningKey = providers.gradleProperty("signingInMemoryKey").isPresent
                || providers.gradleProperty("signing.keyId").isPresent
        if (hasSigningKey) {
            signAllPublications()
        }

        coordinates(group.toString(), name, version.toString())

        pom {
            name.set(this@subprojects.name)
            description.set(moduleDescriptions.getValue(this@subprojects.name))
            url.set("https://github.com/ghals5737/loadmin")
            inceptionYear.set("2026")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("ghals5737")
                    name.set("ghals5737")
                    email.set("ghals5737@gmail.com")
                    url.set("https://github.com/ghals5737")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/ghals5737/loadmin.git")
                developerConnection.set("scm:git:ssh://git@github.com/ghals5737/loadmin.git")
                url.set("https://github.com/ghals5737/loadmin")
            }
        }
    }
}
