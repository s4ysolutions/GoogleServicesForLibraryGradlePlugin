package solutions.s4y.gms.googleservices

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.tasks.InputFile

/**
 * The purpose of this task to allow an access Google Services from the library modules
 *
 * Instead of depending on the application life cycle to init the Firebase automatically
 * this plugin provides the task GenerateGoogleServicesOptionsBuilderTask to generate
 * `googleServicesOptionsBuilder` function which in turn can be called in any suitable moment.
 */

abstract class GenerateGoogleServicesOptionsBuilderTask : DefaultTask() {
    @get:Input
    abstract val googleCloudAppId: Property<String>

    @get:Input
    abstract val classPackage: Property<String>

    @get:InputFile
    abstract val jsonFile: Property<File>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty


    @TaskAction
    fun generate() {
        val quickstartFile = jsonFile.get()

        val intermediateDir = outputDir.get().asFile
        intermediateDir.deleteRecursively()

        if (!intermediateDir.mkdirs()) {
            throw GradleException("Failed to create folder: $intermediateDir")
        }

        val root = JsonParser.parseReader(quickstartFile.bufferedReader(Charsets.UTF_8))
        if (!root.isJsonObject) {
            throw GradleException("Malformed root json at ${quickstartFile.absolutePath}")
        }
        val rootObject = root.asJsonObject

        val projectInfo: JsonObject =
            rootObject.getAsJsonObject("project_info")
                ?: throw GradleException("Missing project_info object")
/*
        Has no counterpart in the FirebaseOptions.Builder
        val projectNumber =
            projectInfo.getAsJsonPrimitive("project_number")?.asString
                ?: throw GradleException("Missing project_info/project_number object")
*/
        val projectId =
            projectInfo.getAsJsonPrimitive("project_id")?.asString
                ?: throw GradleException("Missing project_info/project_id object")

        val firebaseUrl = projectInfo.getAsJsonPrimitive("firebase_url")?.asString

        val bucketName = projectInfo.getAsJsonPrimitive("storage_bucket")?.asString

        val clientArray = rootObject.getAsJsonArray("client")
        if (clientArray == null || !clientArray.isJsonArray) {
            throw GradleException("[client] is either missing or not an array")
        }

        val clientObject = clientArray.let{
            val count = it.size()
            for (i in 0 until count) {
                val clientElement = it[i]
                if (clientElement == null || !clientElement.isJsonObject) {
                    continue
                }
                val clientObject = clientElement.asJsonObject
                val clientInfo = clientObject.getAsJsonObject("client_info") ?: continue
                val androidClientInfo =
                    clientInfo.getAsJsonObject("android_client_info") ?: continue
                val clientPackageName =
                    androidClientInfo.getAsJsonPrimitive("package_name")?.asString ?: continue
                if (clientPackageName == googleCloudAppId.get()) {
                    return@let clientObject
                }
            }
            null
        } ?: throw GradleException("Missing client.client_info.android_client_info[package_name] == ${googleCloudAppId.get()}")

        val apiKey: String = clientObject.getAsJsonArray("api_key")?.let { array ->
                val count = array.size()
                for (i in 0 until count) {
                    val apiKeyElement = array[i]
                    if (apiKeyElement == null || !apiKeyElement.isJsonObject) {
                        continue
                    }
                    val apiKeyObject = apiKeyElement.asJsonObject
                    val currentKey = apiKeyObject.getAsJsonPrimitive("current_key") ?: continue

                    return@let currentKey.asString
                }
                null
            } ?: throw GradleException("Missing api_key/current_key object")

        val clientInfoObject = clientObject.getAsJsonObject("client_info")
            ?: throw GradleException("Missing client[].client_info object")

        val googleAppId = clientInfoObject.getAsJsonPrimitive("mobilesdk_app_id")?.asString
            ?: throw GradleException("Missing client[].client_info.mobilesdk_app_id object")

        /* For further parsing see
         * https://github.com/google/play-services-plugins/blob/master/google-services-plugin/src/main/kotlin/com/google/gms/googleservices/GoogleServicesTask.kt#L108
         */

        val file = outputDir.get().asFile
        file.mkdirs()
        val buildConfigFile = File(file, "googleServicesOptionsBuilder.kt")
        buildConfigFile.parentFile.mkdirs()
        buildConfigFile.writeText(
            """
            package ${classPackage.get()}
            import com.google.firebase.FirebaseOptions

            fun googleServicesOptionsBuilder():FirebaseOptions.Builder =
               FirebaseOptions.Builder()
                 .setApiKey("$apiKey")
                 .setProjectId("$projectId")
                 .setApplicationId("$googleAppId")
                 .setDatabaseUrl("$firebaseUrl")
                 .setStorageBucket("$bucketName")
            """.trimIndent()
        )
    }

}