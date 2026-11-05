package com.example.rewrite.dsl

import org.junit.jupiter.api.Test
import org.openrewrite.maven.Assertions.pomXml
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.SourceSpec

class UpdateKotlinLoggingVersionTest : RewriteTest {

    override fun defaults(spec: RecipeSpec) {
        spec.recipe(UpdateKotlinLoggingVersion)
    }

    private fun pom(body: String, packaging: String = "pom") =
        """
        <project>
            <modelVersion>4.0.0</modelVersion>
            <groupId>com.acme</groupId>
            <artifactId>parent</artifactId>
            <version>1.0.0</version>
            <packaging>$packaging</packaging>
        $body
        </project>
        """.trimIndent()

    private fun dependencies(version: String, indent: String = "    ") =
        """
        |${indent}<dependencies>
        |$indent    <dependency>
        |$indent        <groupId>io.github.oshai</groupId>
        |$indent        <artifactId>kotlin-logging-jvm</artifactId>
        |$indent        <version>$version</version>
        |$indent    </dependency>
        |$indent</dependencies>
        """.trimMargin()

    @Test
    fun `updates a literal version in dependencies`() = rewriteRun(
        pomXml(pom(dependencies("7.0.13")), pom(dependencies("7.0.14"))),
    )

    @Test
    fun `updates a literal version in dependencyManagement`() = rewriteRun(
        pomXml(
            pom("    <dependencyManagement>\n${dependencies("7.0.13", "        ")}\n    </dependencyManagement>"),
            pom("    <dependencyManagement>\n${dependencies("7.0.14", "        ")}\n    </dependencyManagement>"),
        ),
    )

    @Test
    fun `updates the property when the version is a property reference`() = rewriteRun(
        pomXml(
            pom("    <properties>\n        <logging.version>7.0.13</logging.version>\n    </properties>\n" + dependencies("\${logging.version}")),
            pom("    <properties>\n        <logging.version>7.0.14</logging.version>\n    </properties>\n" + dependencies("\${logging.version}")),
        ),
    )

    @Test
    fun `is a no-op when the version is already current`() = rewriteRun(
        pomXml(pom(dependencies("7.0.14"))),
    )

    @Test
    fun `updates the root pom of a single-module project`() = rewriteRun(
        pomXml(pom(dependencies("7.0.13"), "jar"), pom(dependencies("7.0.14"), "jar")),
    )

    @Test
    fun `updates a parent pom in a subfolder of the git repository`() = rewriteRun(
        pomXml(pom(dependencies("7.0.13"), "jar"), pom(dependencies("7.0.14"), "jar")) { spec: SourceSpec<*> ->
            spec.path("services/api/pom.xml")
        },
    )

    @Test
    fun `leaves a child module's own pom alone`() = rewriteRun(
        pomXml(pom(dependencies("7.0.13")), pom(dependencies("7.0.14"))),
        pomXml(
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>com.acme</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                </parent>
                <artifactId>child</artifactId>
            ${dependencies("7.0.13")}
            </project>
            """.trimIndent(),
        ) { spec: SourceSpec<*> -> spec.path("child/pom.xml") },
    )
}
