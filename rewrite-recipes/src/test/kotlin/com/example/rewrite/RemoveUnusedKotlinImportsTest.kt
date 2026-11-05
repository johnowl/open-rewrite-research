package com.example.rewrite

import org.junit.jupiter.api.Test
import org.openrewrite.kotlin.Assertions.kotlin
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest

open class RemoveUnusedKotlinImportsTest : RewriteTest {

    override fun defaults(spec: RecipeSpec) {
        spec.recipe(RemoveUnusedKotlinImports())
    }

    @Test
    fun `removes unused imports and keeps used ones`() = rewriteRun(
        kotlin(
            """
            package com.example

            import java.time.Instant
            import java.util.UUID
            import java.time.Duration

            class A {
                val id: UUID = UUID.randomUUID()
            }
            """.trimIndent(),
            """
            package com.example

            import java.util.UUID

            class A {
                val id: UUID = UUID.randomUUID()
            }
            """.trimIndent(),
        ),
    )

    @Test
    fun `keeps spacing after the package when the first import is removed`() = rewriteRun(
        kotlin(
            """
            package com.example

            import java.time.Instant
            import java.util.UUID

            val id: UUID = UUID.randomUUID()
            """.trimIndent(),
            """
            package com.example

            import java.util.UUID

            val id: UUID = UUID.randomUUID()
            """.trimIndent(),
        ),
    )

    @Test
    fun `removes every import when none is used`() = rewriteRun(
        kotlin(
            """
            package com.example

            import java.time.Instant
            import java.util.UUID

            class A
            """.trimIndent(),
            """
            package com.example

            class A
            """.trimIndent(),
        ),
    )

    @Test
    fun `does not leave a blank first line in a file without a package`() = rewriteRun(
        kotlin(
            """
            import java.time.Instant

            class A
            """.trimIndent(),
            """
            class A
            """.trimIndent(),
        ),
    )

    @Test
    fun `an alias is used through its alias, not its original name`() = rewriteRun(
        kotlin(
            """
            import java.util.UUID as Id
            import java.time.Instant as Moment

            class UUID
            class Instant
            val a: Id = Id.randomUUID()
            val b = Instant()
            """.trimIndent(),
            """
            import java.util.UUID as Id

            class UUID
            class Instant
            val a: Id = Id.randomUUID()
            val b = Instant()
            """.trimIndent(),
        ),
    )

    @Test
    fun `keeps star imports`() = rewriteRun(
        kotlin(
            """
            import java.util.*

            class A
            """.trimIndent(),
        ),
    )

    @Test
    fun `keeps imports with an attached comment`() = rewriteRun(
        kotlin(
            """
            package com.example

            // needed for the reflection hack below
            import java.time.Instant

            class A
            """.trimIndent(),
        ),
    )

    @Test
    fun `keeps operator imports that the syntax uses implicitly`() = rewriteRun(
        kotlin(
            """
            package com.example.ops

            import kotlin.properties.getValue
            import kotlin.collections.component1
            import kotlin.collections.iterator

            class A {
                val value by lazy { 1 }
            }
            """.trimIndent(),
        ),
    )

    @Test
    fun `keeps imports referenced from KDoc but not ones merely mentioned in prose`() = rewriteRun(
        kotlin(
            """
            import java.time.Instant
            import java.time.Duration
            import java.util.UUID
            import java.time.Period

            /**
             * Linked to [Instant], and [the duration][Duration].
             * Mentions Period only in prose.
             *
             * @see UUID
             */
            class A
            """.trimIndent(),
            """
            import java.time.Instant
            import java.time.Duration
            import java.util.UUID

            /**
             * Linked to [Instant], and [the duration][Duration].
             * Mentions Period only in prose.
             *
             * @see UUID
             */
            class A
            """.trimIndent(),
        ),
    )

    @Test
    fun `recognizes every syntactic position a name can be used in`() = rewriteRun(
        kotlin(
            """
            @file:JvmName("Everything")

            package com.example.positions

            import java.io.Closeable
            import java.io.File
            import java.io.IOException
            import java.io.Serializable
            import java.math.BigDecimal
            import java.math.BigInteger
            import java.net.URI
            import java.nio.charset.StandardCharsets
            import java.time.DayOfWeek.MONDAY
            import java.time.Duration
            import java.time.Instant
            import java.time.LocalDate
            import java.time.LocalTime
            import java.time.Period
            import java.util.ArrayDeque
            import java.util.Locale
            import java.util.Optional
            import java.util.UUID
            import java.util.concurrent.TimeUnit
            import java.util.concurrent.atomic.AtomicInteger
            import kotlin.math.absoluteValue
            import kotlin.math.max
            import kotlin.reflect.KClass
            import kotlin.annotation.AnnotationTarget

            typealias Name = UUID

            @Target(AnnotationTarget.CLASS)
            annotation class Marker(val type: KClass<*>)

            @Marker(Locale::class)
            class Everything<T>(private val delegate: Closeable, val file: File?) : Closeable by delegate, Serializable where T : Comparable<LocalDate> {
                val nullable: BigDecimal? = null
                val generic: List<BigInteger> = emptyList()
                val function: (URI) -> Instant = { Instant.now() }
                val template = "${'$'}{StandardCharsets.UTF_8} ${'$'}{MONDAY}"
                val reference = Duration::ofDays
                val infix = 1.absoluteValue to max(1, 2)

                fun checks(value: Any): Boolean = when (value) {
                    is Period -> true
                    is ArrayDeque<*> -> value as? Optional<*> == null
                    else -> false
                }

                fun rescue() = try {
                    LocalTime.MIDNIGHT
                } catch (e: IOException) {
                    TimeUnit.DAYS
                }

                val anonymous = object : AtomicInteger() {}
            }
            """.trimIndent(),
        ),
    )
}
