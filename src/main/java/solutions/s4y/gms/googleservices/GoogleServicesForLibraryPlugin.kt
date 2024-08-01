package solutions.s4y.gms.googleservices

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

class GoogleServicesForLibraryPlugin : Plugin<Project> {
    companion object {
        private const val JSON_FILE_NAME = "google-services.json"
    }

    private fun getJsonFilePaths(
        buildType: String,
        flavorNames: Collection<String>,
        root: File
    ): List<File> {
        val fileLocations: MutableList<String> = ArrayList()
        val flavorName =
            flavorNames.stream()
                .reduce("") { a, b -> a + if (a.isEmpty()) b else b.capitalized() }
        fileLocations.add("")
        fileLocations.add("src/$flavorName/$buildType")
        fileLocations.add("src/$buildType/$flavorName")
        fileLocations.add("src/$flavorName")
        fileLocations.add("src/$buildType")
        fileLocations.add("src/" + flavorName + buildType.capitalized())
        fileLocations.add("src/$buildType")
        var fileLocation = "src"
        for (flavor in flavorNames) {
            fileLocation += "/$flavor"
            fileLocations.add(fileLocation)
            fileLocations.add("$fileLocation/$buildType")
            fileLocations.add(fileLocation + buildType.capitalized())
        }
        return fileLocations
            .asSequence()
            .distinct()
            .sortedByDescending { path -> path.count { it == '/' } }
            .map { location: String ->
                if (location.isEmpty()) location + JSON_FILE_NAME else "$location/$JSON_FILE_NAME"
            }.map {
                root.resolve(it)
            }
            .toList()
    }

    private fun getJsonFiles(
        buildType: String,
        flavorNames: Collection<String>,
        root: File
    ): List<File> = getJsonFilePaths(buildType, flavorNames, root).filter { it.isFile }.toList()


    override fun apply(project: Project) {
        project.extensions.create<GoogleServicesExtension>("googleServiceLibrary")
        project.extensions.findByType<LibraryAndroidComponentsExtension>()?.let {
            project.extensions.configure<LibraryAndroidComponentsExtension>() {
                project.addGenerateGoogleServicesOptionsBuilderTask(this)
            }
        }
    }

    /**
     * Simply speaking it creates `addGenerateGoogleServicesOptionsBuilder` function
     * for each `androidTest` variant
     *
     * To do so it first register GenerateGoogleServicesOptionsBuilderTask task
     * and next makes tasks.withType<KotlinCompile> to depend on it
     *
     * I have to add this because `google-services` plugin does not work with library modules
     */
    internal fun Project.addGenerateGoogleServicesOptionsBuilderTask(extension: LibraryAndroidComponentsExtension) {
        extension.onVariants { variant ->
            val java = variant.sources.java
            if (java == null) {
                println(" ERROR: variant ${variant.name} does not have sourceSet, the file won't be in path.")
            } else {
                val buildType = variant.buildType.orEmpty()
                // AndroidTest available only for debug variant?
                if (buildType == "debug") {
                    // The purpose of the plugin to run tests
                    // listOf("main", "androidTest").forEach { setName ->
                    listOf("androidTest").forEach { setName ->
                        val taskName =
                            "generateGoogleServicesOptionsBuilder${setName.capitalized()}${variant.name.capitalized()}"
                        println("    registerTask: $taskName")

                        val provider = tasks.register(taskName, GenerateGoogleServicesOptionsBuilderTask::class.java) {
                            val googleServicesExtension =
                                project.extensions.getByType<GoogleServicesExtension>()
                            val googleCloudAppId = googleServicesExtension.googleCloudAppId
                            if (googleCloudAppId == null) {
                                val message = """
                            ERROR: googleCloudAppId is null, the file won't be in path.
                            Please add the following to your build.gradle.kts:
                            ```
                            googleServiceLibrary {
                                googleCloudAppId = "your.google.cloud.app.id"
                            }
                            ```
                            Usually googleCloudAppId is the same as the package name of your app.
                            
                            Also it must be the same as one of "client.client_info.android_client_info.package_name"
                            entries in the google-services.json file. In fact googleCloudAppId is used to find
                            the correct "client" entry in the google-services.json file.
                            """
                                throw GradleException(message)
                            }
                            val classPackage = googleServicesExtension.classPackage ?: googleCloudAppId
                            println("The class will be in package: $classPackage, use googleServiceLibrary.classPackage to change it")

                            this.googleCloudAppId.set(googleCloudAppId)
                            this.classPackage.set(classPackage)
                            val productFlavors = variant.productFlavors.map { it.second }

                            val jsonFiles = getJsonFiles(
                                buildType,
                                productFlavors,
                                projectDir
                            )
                            if (jsonFiles.isEmpty()) {
                                val message =
                                    """
                File $JSON_FILE_NAME is missing. 
                The Google Services Plugin cannot function without it. 
                Searched locations: ${
                                        getJsonFilePaths(
                                            buildType,
                                            productFlavors,
                                            projectDir
                                        ).joinToString { "\n" }
                                    }
                """
                                        .trimIndent()

                                throw GradleException(message)
                            }

                            jsonFile.set(jsonFiles.first())
                            description =
                                "generates generateGoogleServicesOptionsBuilder.kt with googleServicesOptionsBuilder()"
                            group = "Custom"
                        }
                        java.addGeneratedSourceDirectory(provider) {
                            /*
                            if (it.outputDir.isPresent) {
                                println("    add sources path: [${java.name}] ${it.outputDir.get()}")
                            }
                             */
                            it.outputDir
                        }
                        tasks.withType<KotlinCompile> {
                            if (name.startsWith("compile${buildType.capitalized()}${setName.capitalized()}"))
                                println("    add dependency on $taskName to ${this.name}")
                            dependsOn(provider.get())
                        }
                    }
                }
            }
        }
    }
}