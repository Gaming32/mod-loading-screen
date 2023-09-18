plugins {
    id("fabric-loom") version "1.3.+"
    id("io.github.juuxel.loom-quiltflower") version "1.8.0"
    `maven-publish`
}

operator fun Project.get(prop: String) = extra[prop] as String

version = project["mod_version"]
group = project["maven_group"]
base.archivesName.set(project["archives_base_name"])

val api by sourceSets.registering {
    compileClasspath += sourceSets.main.get().compileClasspath
}

repositories {
    maven("https://maven.lenni0451.net/releases")
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.extra["minecraft_version"]}")
    mappings("net.fabricmc:yarn:${project.extra["yarn_mappings"]}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.extra["loader_version"]}")

    include(implementation("net.lenni0451:Reflect:1.0.2")!!)
    include(implementation("com.formdev:flatlaf:3.0")!!)
}

tasks.jar {
    includeEmptyDirs = false
    manifest {
        attributes["Premain-Class"] = "io.github.gaming32.modloadingscreen.EarlyLoadingAgent"
    }
}

val apiJar by tasks.registering(Jar::class) {
    from(api.get().output)
    archiveClassifier.set("api")
}
tasks.build {
    dependsOn(apiJar)
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

val targetJavaVersion = 8
tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    if (JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
    withSourcesJar()
}

tasks.named<Jar>("sourcesJar") {
    from(api.get().allSource)
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(apiJar.get().archiveFile) {
                classifier = "api"
            }
        }
    }

    repositories {
        mavenLocal()
    }
}
