plugins {
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "io.github.ghals5737"
    version = "0.0.1-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
