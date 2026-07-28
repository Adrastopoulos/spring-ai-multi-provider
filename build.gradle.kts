plugins {
    java
    alias(libs.plugins.spring.boot) apply false
}

val springBootVersion = libs.versions.springBoot.get()
val springAiVersion = libs.versions.springAi.get()

allprojects {
    group = "io.github.adrastopoulos"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "implementation"(platform("org.springframework.ai:spring-ai-bom:$springAiVersion"))
        "annotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "testImplementation"(platform("org.springframework.ai:spring-ai-bom:$springAiVersion"))
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
