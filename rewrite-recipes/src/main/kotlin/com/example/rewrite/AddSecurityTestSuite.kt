package com.example.rewrite

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.openrewrite.ExecutionContext
import org.openrewrite.Option
import org.openrewrite.ScanningRecipe
import org.openrewrite.SourceFile
import org.openrewrite.Tree
import org.openrewrite.TreeVisitor
import org.openrewrite.Validated
import org.openrewrite.java.tree.J
import org.openrewrite.kotlin.KotlinParser
import org.openrewrite.kotlin.tree.K
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence

/**
 * Generates a JUnit test class in `{basepackage}.security`, once per module, where the base
 * package is the package of the module's `@SpringBootApplication` class.
 */
data class AddSecurityTestSuite
    @JsonCreator
    constructor(
        @field:Option(
            displayName = "Sub-package",
            description = "Package to create the test in, relative to the base package.",
            example = "security",
            required = false,
        )
        @param:JsonProperty("subPackage")
        val subPackage: String? = null,
        @field:Option(
            displayName = "Class name",
            description =
                "Simple name of the generated test class. Maven Surefire only runs classes " +
                    "named `*Test`, `*Tests`, `Test*`, or `*TestCase` by default.",
            example = "SecurityTests",
            required = false,
        )
        @param:JsonProperty("className")
        val className: String? = null,
    ) : ScanningRecipe<AddSecurityTestSuite.Accumulator>() {
        class Accumulator {
            /** Module root (`""` for a single-module project) to the base package of that module. */
            val basePackages = sortedMapOf<String, String>()
            val existingPaths = mutableSetOf<Path>()
        }

        override fun getDisplayName(): String = "Add a security JUnit test suite"

        override fun getDescription(): String =
            "Adds a JUnit test class to the `security` sub-package of the base package of each Spring " +
                "Boot module. Modules that already contain the class are left unchanged."

        override fun validate(): Validated<Any> =
            super
                .validate()
                .and(Validated.test("subPackage", "must be a dotted package name", resolvedSubPackage) { PACKAGE.matches(it) })
                .and(Validated.test("className", "must be a class name", resolvedClassName) { CLASS_NAME.matches(it) })

        override fun getInitialValue(ctx: ExecutionContext) = Accumulator()

        override fun getScanner(acc: Accumulator): TreeVisitor<*, ExecutionContext> =
            object : TreeVisitor<Tree, ExecutionContext>() {
                override fun visit(
                    tree: Tree?,
                    ctx: ExecutionContext,
                ): Tree? {
                    if (tree is SourceFile) {
                        acc.existingPaths.add(tree.sourcePath)
                    }
                    if (tree is K.CompilationUnit) {
                        recordBasePackage(tree, acc)
                    }
                    return tree
                }
            }

        override fun generate(
            acc: Accumulator,
            ctx: ExecutionContext,
        ): Collection<SourceFile> =
            acc.basePackages.flatMap { (moduleRoot, basePackage) ->
                val testPackage = "$basePackage.$resolvedSubPackage"
                val path = Paths.get("${moduleRoot}src/test/kotlin/${testPackage.replace('.', '/')}/$resolvedClassName.kt")
                if (path in acc.existingPaths) {
                    emptyList()
                } else {
                    KotlinParser
                        .builder()
                        .build()
                        .parse(ctx, testSuiteSource(testPackage))
                        .asSequence()
                        .map { it.withSourcePath<SourceFile>(path) }
                        .toList()
                }
            }

        private fun recordBasePackage(
            cu: K.CompilationUnit,
            acc: Accumulator,
        ) {
            val path = cu.sourcePath.toString().replace('\\', '/')
            val sourceRoot = path.indexOf(MAIN_SOURCE_ROOT)
            val isModuleRootRelative = sourceRoot == 0 || (sourceRoot > 0 && path[sourceRoot - 1] == '/')
            if (!isModuleRootRelative || !cu.declaresSpringBootApplication()) {
                return
            }
            val basePackage = cu.packageDeclaration?.packageName ?: return
            // A module with several applications gets one suite, in the outermost package, so that
            // `@SpringBootTest` is not ambiguous about which application it starts.
            acc.basePackages.merge(path.substring(0, sourceRoot), basePackage) { a, b -> minOf(a, b, PACKAGE_ORDER) }
        }

        private fun K.CompilationUnit.declaresSpringBootApplication() =
            statements
                .filterIsInstance<J.ClassDeclaration>()
                .any { c -> c.leadingAnnotations.any { it.simpleName == "SpringBootApplication" } }

        private fun testSuiteSource(testPackage: String) =
            """
        |package $testPackage
        |
        |import org.junit.jupiter.api.Test
        |import org.springframework.boot.test.context.SpringBootTest
        |
        |@SpringBootTest
        |class $resolvedClassName {
        |
        |    @Test
        |    fun contextLoads() {
        |    }
        |}
        |
            """.trimMargin()

        private val resolvedSubPackage get() = subPackage ?: "security"
        private val resolvedClassName get() = className ?: "SecurityTests"

        private companion object {
            const val MAIN_SOURCE_ROOT = "src/main/kotlin/"
            val PACKAGE = Regex("""[a-z_][a-z0-9_]*(\.[a-z_][a-z0-9_]*)*""")
            val CLASS_NAME = Regex("""[A-Z][A-Za-z0-9_]*""")
            val PACKAGE_ORDER = compareBy<String>({ it.count { c -> c == '.' } }, { it })
        }
    }
