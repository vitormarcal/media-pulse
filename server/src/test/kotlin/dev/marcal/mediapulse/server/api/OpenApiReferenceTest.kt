package dev.marcal.mediapulse.server.api

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenApiReferenceTest {
    @Test
    fun `all local schema references resolve`() {
        val specification = Files.readString(openApiPath())
        val schemaNames =
            SCHEMA_DECLARATION
                .findAll(specification)
                .map { it.groupValues[1] }
                .toSet()
        val referencedSchemaNames =
            SCHEMA_REFERENCE
                .findAll(specification)
                .map { it.groupValues[1] }
                .toSet()
        val missingSchemaNames = referencedSchemaNames - schemaNames

        assertTrue(
            missingSchemaNames.isEmpty(),
            "OpenAPI references schemas that are not declared: ${missingSchemaNames.sorted().joinToString()}",
        )
    }

    private fun openApiPath(): Path =
        listOf(
            Path.of("docs/openapi.yaml"),
            Path.of("../docs/openapi.yaml"),
        ).firstOrNull(Files::exists)
            ?: error("Could not find docs/openapi.yaml from ${Path.of("").toAbsolutePath()}")

    private companion object {
        val SCHEMA_DECLARATION = Regex("(?m)^    ([A-Za-z0-9._-]+):$")
        val SCHEMA_REFERENCE = Regex("#/components/schemas/([A-Za-z0-9._-]+)")
    }
}
