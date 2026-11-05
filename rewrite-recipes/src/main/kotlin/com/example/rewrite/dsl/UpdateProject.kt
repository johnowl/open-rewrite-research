package com.example.rewrite.dsl

import org.openrewrite.Recipe
import org.openrewrite.recipes

val UpdateProject: Recipe = recipes(
    displayName = "Update the project (DSL)",
    description = "Removes unused imports, adds the security test suite, and updates kotlin-logging.",
    RemoveUnusedImports,
    AddSecurityTests(),
    UpdateKotlinLoggingVersion,
)
