/*
 * Copyright 2025 Chuanwise and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "cn.chuanwise"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.reflect)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("updateReadmeVersion") {
    val file = File("README.md")
    val pattern = "\${version}"
    val replacement = version.toString()

    file.writeText(file.readText().replace(pattern, replacement))
}

tasks.publish {
    dependsOn("updateReadmeVersion")
}

publishing {
    repositories {
        maven {
            val snapshot = version.toString().contains("snapshot", ignoreCase = true)
            val suffix = if (snapshot) "snapshots" else "releases"

            name = "Chuanwise"
            url = uri("https://nexus.chuanwise.cn/repository/maven-${suffix}")

            fun find(key: String, env: String): String? {
                return rootProject.findProperty(key) as String? ?: System.getProperty(key) ?: System.getenv(env)
            }

            credentials {
                username = find("nexus.username", "NEXUS_USERNAME")
                password = find("nexus.password", "NEXUS_PASSWORD")
            }
        }
    }

    publications {
        create<MavenPublication>("product") {
            artifactId = "kotlin-type-resolver"

            artifact(tasks.kotlinSourcesJar)

            from(components["java"])
        }
    }
}