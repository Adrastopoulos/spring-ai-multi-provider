plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":starter"))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.ai.client.chat)

    testImplementation(libs.spring.boot.starter.test)
}
