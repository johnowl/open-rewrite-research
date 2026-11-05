package com.example.rewrite

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.openrewrite.ExecutionContext
import org.openrewrite.Option
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.maven.MavenIsoVisitor
import org.openrewrite.maven.tree.MavenResolutionResult
import org.openrewrite.xml.XPathMatcher
import org.openrewrite.xml.tree.Xml

/**
 * Sets a dependency's version in the parent POM, which is any POM with `pom` packaging or without
 * a parent in the same build. Child modules are left alone.
 */
data class UpdateParentPomDependencyVersion @JsonCreator constructor(
    @field:Option(displayName = "Group ID", description = "The dependency's group ID.", example = "com.google.guava")
    @param:JsonProperty("groupId")
    val groupId: String,

    @field:Option(displayName = "Artifact ID", description = "The dependency's artifact ID.", example = "guava")
    @param:JsonProperty("artifactId")
    val artifactId: String,

    @field:Option(displayName = "New version", description = "The exact version to set.", example = "33.4.0-jre")
    @param:JsonProperty("newVersion")
    val newVersion: String,
) : Recipe() {

    override fun getDisplayName(): String = "Update a dependency version in the parent POM"

    override fun getDescription(): String =
        "Sets the version of a dependency declared in the parent POM's `dependencies` or " +
            "`dependencyManagement`. A version held in a property of the same POM is updated in " +
            "that property, which also affects anything else that uses the property."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> = object : MavenIsoVisitor<ExecutionContext>() {
        private var versionProperties = emptySet<String>()

        override fun visitDocument(document: Xml.Document, ctx: ExecutionContext): Xml.Document {
            if (!document.isParentPom()) {
                return document
            }
            versionProperties = document.root.dependencyTags()
                .filter { it.isTargetDependency() }
                .mapNotNull { it.getChildValue("version").orElse(null) }
                .mapNotNull { PROPERTY_REFERENCE.matchEntire(it.trim())?.groupValues?.get(1) }
                .toSet()

            val updated = super.visitDocument(document, ctx)
            if (updated !== document) {
                maybeUpdateModel()
            }
            return updated
        }

        override fun visitTag(tag: Xml.Tag, ctx: ExecutionContext): Xml.Tag {
            val visited = super.visitTag(tag, ctx)
            return when {
                PROPERTY.matches(cursor) && visited.name in versionProperties -> visited.withVersion()
                DEPENDENCY_VERSIONS.any { it.matches(cursor) } && isTargetVersionLiteral(visited) -> visited.withVersion()
                else -> visited
            }
        }

        private fun isTargetVersionLiteral(version: Xml.Tag): Boolean {
            val dependency = cursor.parentTreeCursor.getValue<Xml.Tag>()
            return dependency.isTargetDependency() && version.value.orElse("").trim().startsWith("\${").not()
        }

        private fun Xml.Tag.withVersion(): Xml.Tag =
            if (value.orElse(null) == newVersion) this else withValue(newVersion)
    }

    // Not based on the file path, which is relative to the git root and so varies with the checkout.
    private fun Xml.Document.isParentPom() =
        root.getChildValue("packaging").orElse(null) == "pom" ||
            markers.findFirst(MavenResolutionResult::class.java).map { it.parent == null }.orElse(false)

    private fun Xml.Tag.dependencyTags(): List<Xml.Tag> =
        listOfNotNull(
            getChild("dependencies").orElse(null),
            getChild("dependencyManagement").flatMap { it.getChild("dependencies") }.orElse(null),
        ).flatMap { it.getChildren("dependency") }

    private fun Xml.Tag.isTargetDependency() =
        getChildValue("groupId").orElse(null) == groupId && getChildValue("artifactId").orElse(null) == artifactId

    private companion object {
        val PROPERTY_REFERENCE = Regex("""\$\{(.+)}""")
        val PROPERTY = XPathMatcher("/project/properties/*")
        val DEPENDENCY_VERSIONS = listOf(
            XPathMatcher("/project/dependencies/dependency/version"),
            XPathMatcher("/project/dependencyManagement/dependencies/dependency/version"),
        )
    }
}
