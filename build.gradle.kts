plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("java")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.google.com")
    }
    maven {
        url = uri("https://repository.apache.org/content/repositories/snapshots/")
    }
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Apache Camel
    implementation("org.apache.camel.springboot:camel-spring-boot-starter:3.21.0")
    implementation("org.apache.camel:camel-rest:3.21.0")
    implementation("org.apache.camel:camel-servlet:3.21.0")
    implementation("org.apache.camel:camel-http:3.21.0")
    implementation("org.apache.camel:camel-file:3.21.0")
    implementation("org.apache.camel:camel-csv:3.21.0")
    implementation("org.apache.camel:camel-jackson:3.21.0")
    implementation("org.apache.camel:camel-log:3.21.0")

    // Jackson for JSON/CSV processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv")

    // Google Cloud Vertex AI SDK (Latest)
    implementation("com.google.cloud:google-cloud-aiplatform:3.40.0")

    // Apache POI for DOCX generation
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")

    // Markdown parser for conversion to DOCX
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Spring Boot Configuration Processor
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test Dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.camel:camel-test-spring-junit5:3.21.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}