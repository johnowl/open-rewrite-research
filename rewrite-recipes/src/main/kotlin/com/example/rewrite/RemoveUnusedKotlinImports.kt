package com.example.rewrite

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.Javadoc
import org.openrewrite.java.tree.Space
import org.openrewrite.java.tree.Statement
import org.openrewrite.java.tree.TextComment
import org.openrewrite.kotlin.KotlinIsoVisitor
import org.openrewrite.kotlin.tree.K
import org.openrewrite.kotlin.tree.KSpace

/**
 * Removes imports whose name is never mentioned in the file.
 *
 * The match is by name, not by resolved symbol: Kotlin resolves extension functions and
 * operators in ways that type attribution doesn't reliably expose, and wrongly removing an
 * import breaks the build, whereas wrongly keeping one is harmless. So a name that appears
 * anywhere, even on an unrelated receiver, keeps the import.
 */
class RemoveUnusedKotlinImports : Recipe() {

    override fun getDisplayName(): String = "Remove unused Kotlin imports"

    override fun getDescription(): String =
        "Removes imports that are not referenced in a Kotlin source file. Star imports, operator " +
            "function imports, and imports referenced only from KDoc are kept."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> = object : KotlinIsoVisitor<ExecutionContext>() {
        override fun visitCompilationUnit(cu: K.CompilationUnit, ctx: ExecutionContext): K.CompilationUnit {
            val usage = NameUsage().also { NameCollector().visit(cu, it) }
            if (usage.hasUnparsedCode) {
                return cu
            }

            val kept = cu.imports.filterNot { it.isRemovable(usage.names) }
            if (kept.size == cu.imports.size) {
                return cu
            }

            // The first import's prefix separates it from the package, so it has to survive
            // even when that import doesn't.
            val firstPrefix = cu.imports.first().prefix
            val imports = kept.mapIndexed { i, import -> if (i == 0) import.withPrefix(firstPrefix) else import }
            val updated = cu.withImports(imports)
            return if (imports.isEmpty() && cu.packageDeclaration == null) updated.withoutLeadingBlankLines() else updated
        }
    }

    private fun J.Import.isRemovable(usedNames: Set<String>): Boolean {
        val importedName = qualid.simpleName.unquoted()
        val visibleName = alias?.simpleName?.unquoted() ?: importedName
        return importedName != "*" &&
            !importedName.isImplicitlyCalledOperator() &&
            visibleName !in usedNames &&
            prefix.comments.isEmpty()
    }

    private fun K.CompilationUnit.withoutLeadingBlankLines(): K.CompilationUnit {
        val first = statements.firstOrNull() ?: return this
        if (first.prefix.comments.isNotEmpty()) {
            return this
        }
        return withStatements(listOf<Statement>(first.withPrefix(Space.EMPTY)) + statements.drop(1))
    }

    private fun String.isImplicitlyCalledOperator() = this in OPERATOR_FUNCTIONS || COMPONENT_N.matches(this)

    private class NameUsage {
        val names = mutableSetOf<String>()
        var hasUnparsedCode = false
    }

    private class NameCollector : KotlinIsoVisitor<NameUsage>() {
        override fun visitImport(import: J.Import, usage: NameUsage): J.Import = import

        override fun visitPackage(pkg: J.Package, usage: NameUsage): J.Package = pkg

        override fun visitIdentifier(identifier: J.Identifier, usage: NameUsage): J.Identifier {
            usage.names += identifier.simpleName.unquoted()
            return super.visitIdentifier(identifier, usage)
        }

        override fun visitUnknown(unknown: J.Unknown, usage: NameUsage): J.Unknown {
            usage.hasUnparsedCode = true
            return unknown
        }

        override fun visitSpace(space: Space?, location: Space.Location, usage: NameUsage): Space {
            space?.collectKdocReferences(usage)
            return super.visitSpace(space, location, usage)
        }

        override fun visitSpace(space: Space, location: KSpace.Location, usage: NameUsage): Space {
            space.collectKdocReferences(usage)
            return super.visitSpace(space, location, usage)
        }

        private fun Space.collectKdocReferences(usage: NameUsage) {
            comments.forEach { comment ->
                val text = when (comment) {
                    is TextComment -> comment.text.takeIf { comment.isMultiline && it.startsWith("*") }
                    is Javadoc.DocComment -> comment.printComment(cursor)
                    else -> null
                }
                text?.let { usage.names += it.kdocReferences() }
            }
        }

        private fun String.kdocReferences(): List<String> =
            KDOC_REFERENCE.findAll(this)
                .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                .flatMap { it.split('.') }
                .map { it.unquoted() }
                .toList()
    }

    private companion object {
        val COMPONENT_N = Regex("component\\d+")

        // `[Foo]`, `[Foo.bar]`, `[text][Foo]`, `@see Foo`, `@throws Foo`, `@sample foo.bar`.
        val KDOC_REFERENCE = Regex("""\[([\w.`]+)]|@(?:see|throws|exception|sample)\s+([\w.`]+)""")

        // Imported for use by syntax alone, so their name need not appear in the file:
        // `by`, `a[i]`, `a()`, `for (x in y)`, `val (a, b) = y`, and the arithmetic operators.
        val OPERATOR_FUNCTIONS = setOf(
            "getValue", "setValue", "provideDelegate",
            "get", "set", "invoke", "contains",
            "iterator", "next", "hasNext",
            "plus", "minus", "times", "div", "rem", "mod", "rangeTo", "rangeUntil",
            "plusAssign", "minusAssign", "timesAssign", "divAssign", "remAssign", "modAssign",
            "unaryPlus", "unaryMinus", "not", "inc", "dec", "compareTo",
        )

        fun String.unquoted() = removeSurrounding("`")
    }
}
