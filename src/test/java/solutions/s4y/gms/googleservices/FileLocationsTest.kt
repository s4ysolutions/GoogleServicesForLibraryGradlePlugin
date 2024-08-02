package solutions.s4y.gms.googleservices

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File


class GoogleServicesForLibraryPluginTest {
    @Nested
    inner class GetJsonFilePathsTest {

        @Test
        fun getJsonFilePaths_shouldReturnFiles() {
            // Arrange
            val buildType = "debug"
            val flavorNames = listOf("flavor1", "flavor2")
            val root = "/root"
            val plugin = GoogleServicesForLibraryPlugin()
            // Act
            val result = plugin.getPossibleJsonFiles(buildType, flavorNames, File(root))
                .map { it.canonicalPath }
            // Assert
            assertEquals(
                listOf(
                    "/google-services.json",
                    "/root/google-services.json",
                    "/root/src/google-services.json",
                    "/root/src/debug/google-services.json",
                    "/root/src/flavor1/google-services.json",
                    "/root/src/flavor2/google-services.json",
                    "/root/src/debug/flavor1/google-services.json",
                    "/root/src/debug/flavor2/google-services.json",
                ),
                result
            )
        }
    }
}