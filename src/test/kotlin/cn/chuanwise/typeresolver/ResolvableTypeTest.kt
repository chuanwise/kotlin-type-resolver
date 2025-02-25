/*
 * Copyright 2025 Chuanwise and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.chuanwise.typeresolver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private typealias Str = String

class ResolvableTypeTest {
    interface Simple

    @Test
    fun testSimple() {
        val type = createResolvableType<Simple>()
    }

    @Test
    fun testTypeAlias() {
        val type = createResolvableType<Str>()
    }

    class OuterClass<T> {
        inner class InnerClass<U> {
            fun <R> foo(): R = error("Not implemented")
        }
    }

    interface OuterInterface<T> {
        class InnerClass<U>
    }

    @Test
    fun testNestedClass() {
        val outerType = createResolvableType<OuterClass<Int>>()
        val innerType = createResolvableType<OuterClass<Int>.InnerClass<String>>()

        assertEquals(outerType, innerType.outerType)
        assertEquals(createResolvableType<Int>(), outerType.typeArguments[0].type)
        assertEquals(createResolvableType<String>(), innerType.typeArguments[0].type)
    }

    @Test
    fun testNestedInterface() {
        val outerType = createResolvableType<OuterInterface<Int>>()
        val innerType = createResolvableType<OuterInterface.InnerClass<String>>()

        assertEquals(createResolvableType<Int>(), outerType.typeArguments[0].type)
        assertEquals(createResolvableType<String>(), innerType.typeArguments[0].type)
    }

    interface Foo
    interface FooSon : Foo

    interface Bar<in T : Foo>
    interface Baz<T : Foo>

    @Test
    fun testAssignCheck() {
        val nullableString = createResolvableType<String?>()
        val string = createResolvableType<String>()

        assertTrue(nullableString.isAssignableFrom(string))
        assertFalse(string.isAssignableFrom(nullableString))

        val foo = createResolvableType<Foo>()
        val fooSon = createResolvableType<FooSon>()

        assertTrue(foo.isAssignableFrom(fooSon))
        assertFalse(fooSon.isAssignableFrom(foo))

        val bar = createResolvableType<Bar<Foo>>()
        val barSon = createResolvableType<Bar<FooSon>>()

        assertTrue(bar.isAssignableFrom(barSon))
        assertFalse(barSon.isAssignableFrom(bar))

        val baz = createResolvableType<Baz<Foo>>()
        val bazSon = createResolvableType<Baz<FooSon>>()

        assertFalse(baz.isAssignableFrom(bazSon))
        assertFalse(bazSon.isAssignableFrom(baz))
    }

    interface Foo1<T> {
        class Foo2<U> {
            interface Foo3<R> {
                class Foo4<U>
            }
        }
    }

    @Test
    fun testParseComplexInnerClass() {
        val foo4 = createResolvableType<Foo1.Foo2.Foo3.Foo4<Float>>()
        val foo3 = createResolvableType<Foo1.Foo2.Foo3<Float>>()
        val foo2 = createResolvableType<Foo1.Foo2<Float>>()
        val foo1 = createResolvableType<Foo1<Float>>()
    }

    @Test
    fun testSuperClassParameter() {
        val string = createResolvableType<String>()
        val comparableArgument = string.getTypeArgumentOrFail(Comparable::class, "T").type!!

        assertEquals(string, comparableArgument)
    }

    class Foo3<T> {
        inner class Foo3 {
            inner class Foo3<T, U> {
                inner class Foo3 {
                    fun <G> foo(t: Map<Map<U, List<Map<T, String>>>, U>): Pair<T, Map<U, G>> = error("Not implemented")
                }
            }
        }
    }

    @Test
    fun testComplexMemberClass() {
        val foo3 = createResolvableType<Foo3<Float>.Foo3.Foo3<Double, String>>()
        val innerFoo3 = foo3.memberTypes.single()

        val fooFun = innerFoo3.memberFunctions.single { it.rawFunction.name == "foo" }
        val fooFunT = fooFun.parameters.last()
        assertEquals(createResolvableType<Map<Map<String, List<Map<Double, String>>>, String>>(), fooFunT.type)

        val fooFunReturnType = fooFun.returnType!!
        assertEquals(createResolvableType<Double>(), fooFunReturnType.getTypeArgument<Pair<*, *>>(0)?.type)

        assertEquals(
            createResolvableType<String>(),
            fooFunReturnType.getTypeArgument<Pair<*, *>>(1)?.type?.getTypeArgument<Map<*, *>>("K")?.type
        )
    }
}
