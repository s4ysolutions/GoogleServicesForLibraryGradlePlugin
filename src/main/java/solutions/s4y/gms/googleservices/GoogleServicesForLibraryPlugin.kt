package solutions.s4y.gms.googleservices

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

class GoogleServicesForLibraryPlugin : Plugin<Project> {
    companion object {
        private const val JSON_FILE_NAME = "google-services.json"
    }

    internal fun getPossibleJsonFiles(
        buildType: String, flavorNames: List<String>, root: File
    ): List<File> {
        val fileLocations: MutableList<String> = ArrayList()
        // assume the current directory is the library module root
        // and we have to take a look at the parent directory
        // for the project shared google-services.json file
        fileLocations.add("..")
        fileLocations.add(".")
        fileLocations.add("src")
        fileLocations.add("src/$buildType")
        flavorNames.forEach {
            fileLocations.add("src/$it")
            fileLocations.add("src/$buildType/$it")
        }
        return fileLocations.distinct().map { location: String ->
                if (location.isEmpty()) JSON_FILE_NAME else "$location/$JSON_FILE_NAME"
            }.map {
                root.resolve(it)
            }.sortedBy {
                it.canonicalPath.split("/").size
            }
    }

    private fun getJsonFiles(
        buildType: String, flavorNames: List<String>, root: File
    ): List<File> = getPossibleJsonFiles(buildType, flavorNames, root).filter { it.isFile }


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
        var taskProvider: TaskProvider<GenerateGoogleServicesOptionsBuilderTask>? = null
        extension.onVariants { variant ->
            val java = variant.sources.java
            if (java == null) {
                println(" ERROR: variant ${variant.name} does not have sourceSet, the file won't be in path.")
            } else {
                val buildType = variant.buildType.orEmpty()
                // is seems compileReleaseAndroidTestKotlin does not exist
                listOf("androidTest", "ReleaseK").forEach { setName ->
                    val taskName = "generateGoogleServicesOptionsBuilder"
                    val provider = taskProvider ?: run {
                        val p = tasks.register<GenerateGoogleServicesOptionsBuilderTask>(taskName) {
                            val googleServicesExtension = project.extensions.getByType<GoogleServicesExtension>()
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
                            println("    The class will be in package: $classPackage, use googleServiceLibrary.classPackage to change it")

                            this.googleCloudAppId.set(googleCloudAppId)
                            this.classPackage.set(classPackage)
                            val productFlavors = variant.productFlavors.map { it.second }

                            val jsonFiles = getJsonFiles(
                                buildType, productFlavors, projectDir
                            )
                            if (jsonFiles.isEmpty()) {
                                val paths = getPossibleJsonFiles(
                                    buildType, productFlavors, projectDir
                                ).joinToString("\n") { it.canonicalPath }
                                val message = """
                File $JSON_FILE_NAME is missing. 
                The Google Services Plugin cannot function without it. 
                Searched locations:
                $paths
                """.trimIndent().trimMargin()

                                throw GradleException(message)
                            }

                            jsonFile.set(jsonFiles.first())
                            println("    jsonFile: ${jsonFiles.first().canonicalPath}")
                            description =
                                "generates generateGoogleServicesOptionsBuilder.kt with googleServicesOptionsBuilder()"
                            group = "Custom"
                        }
                        taskProvider = p
                        p
                    }
                    java.addGeneratedSourceDirectory(provider) {
                        if (it.outputDir.isPresent) {
                            //    println("    add sources path: [${java.name}] ${it.outputDir.get()}")
                        }
                        it.outputDir
                    }

                    tasks.withType<KotlinCompile> {
                        if (name.lowercase().startsWith("compile$buildType$setName".lowercase())) {
                            println("    add dependency on $taskName to ${name}")
                            dependsOn(provider.get())
                        }
                    }
                }
            }
        }
    }
}