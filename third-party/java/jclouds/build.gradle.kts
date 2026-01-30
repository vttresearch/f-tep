plugins {
    `java-library`
    id("com.github.johnrengelman.shadow")
}

dependencies {
    api("org.apache.jclouds.api:openstack-cinder")
    api("org.apache.jclouds.api:openstack-keystone")
    api("org.apache.jclouds.api:openstack-neutron")
    api("org.apache.jclouds.api:openstack-nova")
    api("org.apache.jclouds.driver:jclouds-okhttp")
    api("org.apache.jclouds.driver:jclouds-slf4j")
    api("org.apache.jclouds.driver:jclouds-sshj")

    // force the specific versions of jclouds dependencies
    implementation("com.google.inject:guice:7.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.errorprone:error_prone_annotations:2.18.0")
    implementation("com.google.guava:guava:32.0.0-jre")
    implementation("com.squareup.okhttp3:okhttp:3.14.9")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.0.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")
}

configurations.compile {
    exclude(group = "org.slf4j")
}

// Provide a Serializable config property to fix shadowJar caching
data class RelocationConfiguration(
    val pattern: String,
    val destination: String,
    val exclusionPatterns: List<String> = listOf()
) : java.io.Serializable

val relocationConfigs = listOf(
    "com.google.code.gson",
    "com.google.common",
    "com.google.gson",
    "com.google.inject",
    "com.google.thirdparty",
    "okhttp",
    "okio",
    "javax.ws.rs"
).map {
    RelocationConfiguration(it, "shadow.jclouds.$it")
}

tasks {
    "shadowJar"(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        relocationConfigs.forEach { relocationConfig ->
            relocate(relocationConfig.pattern, relocationConfig.destination) {
                relocationConfig.exclusionPatterns.forEach {
                    exclude(it)
                }
            }
        }
        inputs.property("relocationConfigs", relocationConfigs)
        mergeServiceFiles()
    }
}
