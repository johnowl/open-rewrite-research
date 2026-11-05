package com.example.rewrite.dsl

import com.fasterxml.jackson.annotation.JsonIgnore
import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.SourceFile
import org.openrewrite.java.tree.J
import org.openrewrite.kotlin.KotlinParser
import org.openrewrite.kotlin.tree.K
import org.openrewrite.recipe
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import kotlin.streams.asSequence

private const val MAIN_SOURCE_ROOT = "src/main/kotlin/"
private val PACKAGE_ORDER = compareBy<String>({ it.count { c -> c == '.' } }, { it })

/**
 * Adds `{basepackage}.security.SecurityTests` to each Spring Boot module.
 *
 * This is a class around a DSL recipe because the compiler plugin turns a top-level
 * `val x = recipe(...)` into a plain `Recipe`, which never runs `scan` or `generate`.
 */
class AddSecurityTests : Recipe() {
    // Rebuilt rather than serialized: the DSL's anonymous recipe has no creator Jackson can call.
    @field:JsonIgnore
    private val scanningRecipe = addSecurityTestsRecipe()

    override fun getDisplayName(): String = "Add a security JUnit test suite (DSL)"

    override fun getDescription(): String =
        "Adds a JUnit test class to the `security` sub-package of the base package of each Spring Boot module."

    override fun getRecipeList(): List<Recipe> = listOf(scanningRecipe)
}

private class ModuleScan {
    val basePackages = sortedMapOf<String, String>()
    val existingPaths = mutableSetOf<Path>()
}

private fun addSecurityTestsRecipe(): Recipe = recipe(
    displayName = "Generate the security JUnit test suite (DSL)",
    description = "Scans for `@SpringBootApplication` classes and generates a test class under each base package.",
    // Required here: Recipe.validate() rejects the null this would otherwise capture.
    estimatedEffortPerOccurrence = Duration.ofMinutes(5),
) {
    scan(ModuleScan()) { acc ->
        kotlin {
            visitPackage { pkg ->
                acc.existingPaths.add(cursor.firstEnclosingOrThrow(K.CompilationUnit::class.java).sourcePath)
                pkg
            }
            visitClassDeclaration { declaration ->
                if (declaration.isSpringBootApplication()) {
                    val cu = cursor.firstEnclosingOrThrow(K.CompilationUnit::class.java)
                    val path = cu.sourcePath.toString().replace('\\', '/')
                    val sourceRoot = path.indexOf(MAIN_SOURCE_ROOT)
                    val basePackage = cu.packageDeclaration?.packageName
                    if (basePackage != null && (sourceRoot == 0 || (sourceRoot > 0 && path[sourceRoot - 1] == '/'))) {
                        acc.basePackages.merge(path.substring(0, sourceRoot), basePackage) { a, b -> minOf(a, b, PACKAGE_ORDER) }
                    }
                }
                declaration
            }
        }
    }.generate { acc ->
        acc.basePackages.flatMap { (moduleRoot, basePackage) ->
            val testPackage = "$basePackage.security"
            val path = Paths.get("${moduleRoot}src/test/kotlin/${testPackage.replace('.', '/')}/SecurityTests.kt")
            if (path in acc.existingPaths) emptyList() else parseSecurityTests(testPackage, path, ctx)
        }
    }
}

private fun J.ClassDeclaration.isSpringBootApplication() =
    leadingAnnotations.any { it.simpleName == "SpringBootApplication" }

private fun parseSecurityTests(testPackage: String, path: Path, ctx: ExecutionContext): List<SourceFile> =
    KotlinParser.builder().build()
        .parse(
            ctx,
            """
            |package $testPackage
            |
            |import org.junit.jupiter.api.Test
            |import org.springframework.boot.test.context.SpringBootTest
            |
            |@SpringBootTest
            |class SecurityTests {
            |
            |    @Test
            |    fun contextLoads() {
            |    }
            |}
            |
            """.trimMargin(),
        )
        .asSequence()
        .map { it.withSourcePath<SourceFile>(path) }
        .toList()
