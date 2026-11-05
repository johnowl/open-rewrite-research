package com.example.rewrite.dsl

import org.openrewrite.Recipe
import org.openrewrite.dsl.scopes.MavenScope
import org.openrewrite.maven.tree.MavenResolutionResult
import org.openrewrite.recipe
import org.openrewrite.xml.XPathMatcher
import org.openrewrite.xml.tree.Xml

private val PROPERTY_REFERENCE = Regex("""\$\{(.+)}""")
private val PROPERTY = XPathMatcher("/project/properties/*")
private val DEPENDENCY_VERSIONS =
    listOf(
        XPathMatcher("/project/dependencies/dependency/version"),
        XPathMatcher("/project/dependencyManagement/dependencies/dependency/version"),
    )

// DSL recipes are parameterless properties, so each dependency to update gets its own recipe.
val UpdateKotlinLoggingVersion: Recipe =
    recipe(
        displayName = "Update kotlin-logging in the parent POM (DSL)",
        description = "Sets `io.github.oshai:kotlin-logging-jvm` to 7.0.14 in any POM with `pom` packaging or without a parent in the same build.",
    ) {
        edit {
            maven {
                updateParentPomDependencyVersion("io.github.oshai", "kotlin-logging-jvm", "7.0.14")
            }
        }
    }

fun MavenScope.updateParentPomDependencyVersion(
    groupId: String,
    artifactId: String,
    newVersion: String,
) {
    fun Xml.Tag.isTargetDependency() =
        getChildValue("groupId").orElse(null) == groupId && getChildValue("artifactId").orElse(null) == artifactId

    var isParentPom = false
    var versionProperties = emptySet<String>()

    visitDocument { document ->
        val root = document.root
        // Not based on the file path, which is relative to the git root and so varies with the checkout.
        isParentPom = root.getChildValue("packaging").orElse(null) == "pom" ||
            document.markers.findFirst(MavenResolutionResult::class.java).map { it.parent == null }.orElse(false)
        versionProperties =
            listOfNotNull(
                root.getChild("dependencies").orElse(null),
                root.getChild("dependencyManagement").flatMap { it.getChild("dependencies") }.orElse(null),
            ).flatMap { it.getChildren("dependency") }
                .filter { it.isTargetDependency() }
                .mapNotNull { it.getChildValue("version").orElse(null) }
                .mapNotNull { PROPERTY_REFERENCE.matchEntire(it.trim())?.groupValues?.get(1) }
                .toSet()
        document
    }

    visitTag { tag ->
        val isTarget =
            isParentPom &&
                when {
                    PROPERTY.matches(cursor) -> {
                        tag.name in versionProperties
                    }

                    DEPENDENCY_VERSIONS.any { it.matches(cursor) } -> {
                        cursor.parentTreeCursor.getValue<Xml.Tag>().isTargetDependency() &&
                            !tag.value
                                .orElse("")
                                .trim()
                                .startsWith("\${")
                    }

                    else -> {
                        false
                    }
                }
        if (isTarget && tag.value.orElse(null) != newVersion) {
            maybeUpdateModel()
            tag.withValue(newVersion)
        } else {
            tag
        }
    }
}
