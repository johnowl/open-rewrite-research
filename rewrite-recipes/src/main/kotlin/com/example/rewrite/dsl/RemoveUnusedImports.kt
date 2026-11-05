package com.example.rewrite.dsl

import com.example.rewrite.RemoveUnusedKotlinImports
import org.openrewrite.Recipe
import org.openrewrite.recipe

val RemoveUnusedImports: Recipe = recipe(
    displayName = "Remove unused Kotlin imports (DSL)",
    description = "Removes imports that are not referenced in a Kotlin source file. Star imports, operator function imports, and imports referenced only from KDoc are kept.",
) {
    edit {
        // Removing an import means editing K.CompilationUnit, which the `kotlin { }` scope has no
        // hook for, and `visitImport` must return an import. So this reuses a regular visitor.
        check(true, RemoveUnusedKotlinImports().visitor)
    }
}
