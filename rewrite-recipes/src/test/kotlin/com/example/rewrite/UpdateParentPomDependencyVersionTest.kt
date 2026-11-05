package com.example.rewrite

import org.junit.jupiter.api.Test
import org.openrewrite.maven.Assertions.pomXml
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.SourceSpec

class UpdateParentPomDependencyVersionTest : RewriteTest {

    override fun defaults(spec: RecipeSpec) {
        spec.recipe(UpdateParentPomDependencyVersion("org.slf4j", "slf4j-api", "2.0.17"))
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
        |$indent        <groupId>org.slf4j</groupId>
        |$indent        <artifactId>slf4j-api</artifactId>
        |$indent        <version>$version</version>
        |$indent    </dependency>
        |$indent</dependencies>
        """.trimMargin()

    @Test
    fun `updates a literal version in dependencies`() = rewriteRun(
        pomXml(pom(dependencies("2.0.16")), pom(dependencies("2.0.17"))),
    )

    @Test
    fun `updates a literal version in dependencyManagement`() = rewriteRun(
        pomXml(
            pom("    <dependencyManagement>\n${dependencies("2.0.16", "        ")}\n    </dependencyManagement>"),
            pom("    <dependencyManagement>\n${dependencies("2.0.17", "        ")}\n    </dependencyManagement>"),
        ),
    )

    @Test
    fun `updates the property when the version is a property reference`() = rewriteRun(
        pomXml(
            pom("    <properties>\n        <slf4j.version>2.0.16</slf4j.version>\n    </properties>\n" + dependencies("\${slf4j.version}")),
            pom("    <properties>\n        <slf4j.version>2.0.17</slf4j.version>\n    </properties>\n" + dependencies("\${slf4j.version}")),
        ),
    )

    @Test
    fun `leaves other dependencies and unrelated properties alone`() = rewriteRun(
        pomXml(
            pom(
                """
                |    <properties>
                |        <other.version>33.4.0-jre</other.version>
                |    </properties>
                |    <dependencies>
                |        <dependency>
                |            <groupId>com.google.guava</groupId>
                |            <artifactId>guava</artifactId>
                |            <version>${'$'}{other.version}</version>
                |        </dependency>
                |    </dependencies>
                """.trimMargin(),
            ),
        ),
    )

    @Test
    fun `is a no-op when the version is already current`() = rewriteRun(
        pomXml(pom(dependencies("2.0.17"))),
    )

    @Test
    fun `updates the root pom of a single-module project`() = rewriteRun(
        pomXml(pom(dependencies("2.0.16"), "jar"), pom(dependencies("2.0.17"), "jar")),
    )

    @Test
    fun `updates a parent pom in a subfolder of the git repository`() = rewriteRun(
        pomXml(pom(dependencies("2.0.16"), "jar"), pom(dependencies("2.0.17"), "jar")) { spec: SourceSpec<*> ->
            spec.path("services/api/pom.xml")
        },
    )

    @Test
    fun `leaves a child module's own pom alone`() = rewriteRun(
        pomXml(pom(dependencies("2.0.16")), pom(dependencies("2.0.17"))),
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
            ${dependencies("2.0.16")}
            </project>
            """.trimIndent(),
        ) { spec: SourceSpec<*> -> spec.path("child/pom.xml") },
    )
}
