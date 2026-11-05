package com.example.rewrite.dsl

import org.junit.jupiter.api.Test
import org.openrewrite.kotlin.Assertions.kotlin
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.SourceSpec
import org.openrewrite.test.TypeValidation

class AddSecurityTestsTest : RewriteTest {

    override fun defaults(spec: RecipeSpec) {
        spec.recipe(AddSecurityTests())
            .typeValidationOptions(TypeValidation.none())
    }

    private fun application(pkg: String, modulePrefix: String = "") = kotlin(
        """
        package $pkg

        @SpringBootApplication
        class Application

        annotation class SpringBootApplication
        """.trimIndent(),
    ) { spec: SourceSpec<*> -> spec.path("${modulePrefix}src/main/kotlin/${pkg.replace('.', '/')}/Application.kt") }

    private fun securityTests(pkg: String) =
        """
        package $pkg

        import org.junit.jupiter.api.Test
        import org.springframework.boot.test.context.SpringBootTest

        @SpringBootTest
        class SecurityTests {

            @Test
            fun contextLoads() {
            }
        }

        """.trimIndent()

    @Test
    fun `adds the suite under the application's package`() = rewriteRun(
        application("com.acme.shop"),
        kotlin(null, securityTests("com.acme.shop.security")) { spec: SourceSpec<*> ->
            spec.path("src/test/kotlin/com/acme/shop/security/SecurityTests.kt")
        },
    )

    @Test
    fun `adds one suite per module`() = rewriteRun(
        application("com.acme.a", "a/"),
        application("com.acme.b", "b/"),
        kotlin(null, securityTests("com.acme.a.security")) { spec: SourceSpec<*> ->
            spec.path("a/src/test/kotlin/com/acme/a/security/SecurityTests.kt")
        },
        kotlin(null, securityTests("com.acme.b.security")) { spec: SourceSpec<*> ->
            spec.path("b/src/test/kotlin/com/acme/b/security/SecurityTests.kt")
        },
    )

    @Test
    fun `is a no-op when the suite already exists`() = rewriteRun(
        application("com.acme.shop"),
        kotlin("package com.acme.shop.security\n\nclass SecurityTests") { spec: SourceSpec<*> ->
            spec.path("src/test/kotlin/com/acme/shop/security/SecurityTests.kt")
        },
    )

    @Test
    fun `is a no-op without a Spring Boot application`() = rewriteRun(
        kotlin("package com.acme.lib\n\nclass Library") { spec: SourceSpec<*> ->
            spec.path("src/main/kotlin/com/acme/lib/Library.kt")
        },
    )

    @Test
    fun `ignores applications declared in test sources`() = rewriteRun(
        kotlin("package com.acme.fixture\n\n@SpringBootApplication\nclass Fixture\n\nannotation class SpringBootApplication") { spec: SourceSpec<*> ->
            spec.path("src/test/kotlin/com/acme/fixture/Fixture.kt")
        },
    )
}
