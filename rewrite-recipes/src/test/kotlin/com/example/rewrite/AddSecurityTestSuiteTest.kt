package com.example.rewrite

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.kotlin.Assertions.kotlin
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.SourceSpec
import org.openrewrite.test.TypeValidation

class AddSecurityTestSuiteTest : RewriteTest {

    override fun defaults(spec: RecipeSpec) {
        spec.recipe(AddSecurityTestSuite())
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

    private fun suite(pkg: String, name: String = "SecurityTests") =
        """
        package $pkg

        import org.junit.jupiter.api.Test
        import org.springframework.boot.test.context.SpringBootTest

        @SpringBootTest
        class $name {

            @Test
            fun contextLoads() {
            }
        }

        """.trimIndent()

    @Test
    fun `adds the suite under the application's package`() = rewriteRun(
        application("com.acme.shop"),
        kotlin(null, suite("com.acme.shop.security")) { spec: SourceSpec<*> ->
            spec.path("src/test/kotlin/com/acme/shop/security/SecurityTests.kt")
        },
    )

    @Test
    fun `honors the sub-package and class name options`() = rewriteRun(
        { spec -> spec.recipe(AddSecurityTestSuite("auth.web", "LoginTests")) },
        application("com.acme.shop"),
        kotlin(null, suite("com.acme.shop.auth.web", "LoginTests")) { spec: SourceSpec<*> ->
            spec.path("src/test/kotlin/com/acme/shop/auth/web/LoginTests.kt")
        },
    )

    @Test
    fun `adds one suite per module`() = rewriteRun(
        application("com.acme.a", "a/"),
        application("com.acme.b", "b/"),
        kotlin(null, suite("com.acme.a.security")) { spec: SourceSpec<*> ->
            spec.path("a/src/test/kotlin/com/acme/a/security/SecurityTests.kt")
        },
        kotlin(null, suite("com.acme.b.security")) { spec: SourceSpec<*> ->
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

    @Test
    fun `rejects invalid options`() {
        assertThat(AddSecurityTestSuite(subPackage = "Not A Package").validate().isValid).isFalse()
        assertThat(AddSecurityTestSuite(className = "lowercase").validate().isValid).isFalse()
        assertThat(AddSecurityTestSuite().validate().isValid).isTrue()
    }
}
