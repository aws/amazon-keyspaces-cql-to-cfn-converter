plugins {
    kotlin("jvm") version "1.4.10"
    application
    antlr
}
group = "software.aws.mcs"
version = "1.0.0"

repositories {
    mavenCentral()
}
dependencies {
    antlr("org.antlr:antlr:3.5.2")
    implementation("com.github.ajalt.clikt:clikt:3.0.1")
    implementation("software.amazon.awscdk:cassandra:1.70.0")
    testImplementation(kotlin("test-junit5", "1.4.10"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "11"
        dependsOn(generateGrammarSource)
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "11"
        dependsOn(generateTestGrammarSource)
    }
    test {
        useJUnitPlatform()
    }
}
application {
    mainClassName = "software.aws.mcs.cql2cfn.MainKt"
}
