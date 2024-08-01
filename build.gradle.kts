plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "solutions.s4y.gms"
version = "1.0.0-alpha01"

dependencies {
    implementation("com.android.tools.build:gradle:8.5.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
    implementation(kotlin("reflect"))
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    website = "https://github.com/s4ysolutions/GoogleServicesForLibraryGradlePlugin"
    vcsUrl = "https://github.com/s4ysolutions/GoogleServicesForLibraryGradlePlugin.git"
    plugins {
        create("googleServicesForLibrary") {
            id = "solutions.s4y.google-services"
            implementationClass = "solutions.s4y.gms.googleservices.GoogleServicesForLibraryPlugin"
            displayName = "Google Services Plugin for Library Modules"
            tags = listOf("google-services")
            description = """
                    When the plugin is applied to a library modules it creates  `fun googleServicesOptionsBuilder():FirebaseOptions.Builder`"
                    supposed to return the FirebaseOptions.Builder instance with the options from the google-services.json file.
                    This builder can be used to initialize the Firebase in the library module.
                """".trimIndent()
        }
    }
}
/*
tasks.withType<Test>().configureEach {
    dependsOn("publishAllPublicationsToMavenRepository")
}
 */

tasks.withType<Jar>().configureEach {
    from("LICENSE")
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    // group + rootProject.name
    publications {
        create<MavenPublication>("pluginMaven") {

            artifactId = "google-services-library"
        }
    }
    afterEvaluate {
        publications.withType(MavenPublication::class.java) {
            pom {
                url = "https://github.com/s4ysolutions/GoogleServicesForLibraryGradlePlugin"
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id = "s4y.solutions"
                        name = "Sergey Dolin"
                        email = "sergey@s4y.solutions"
                    }
                }
            }
        }
    }
}