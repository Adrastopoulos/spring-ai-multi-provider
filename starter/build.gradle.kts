plugins {
    `java-library`
}

dependencies {
    api(libs.spring.ai.openai)
    implementation(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.ai.client.chat)
}
