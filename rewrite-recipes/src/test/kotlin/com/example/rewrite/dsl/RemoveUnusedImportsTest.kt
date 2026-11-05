package com.example.rewrite.dsl

import com.example.rewrite.RemoveUnusedKotlinImportsTest
import org.openrewrite.test.RecipeSpec

/** Runs every scenario of the class-based recipe's test against the DSL recipe. */
class RemoveUnusedImportsTest : RemoveUnusedKotlinImportsTest() {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(RemoveUnusedImports)
    }
}
