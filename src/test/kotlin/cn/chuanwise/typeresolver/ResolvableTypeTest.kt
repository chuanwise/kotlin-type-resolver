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
import java.net.URI

private typealias Str = String

class ResolvableTypeTest {
    interface Simple

    @Test
    fun testSimple() {
        val type = ResolvableType<Simple>()
    }

    @Test
    fun testTypeAlias() {
        val type = ResolvableType<Str>()
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
        val outerType = ResolvableType<OuterClass<Int>>()
        val innerType = ResolvableType<OuterClass<Int>.InnerClass<String>>()

        assertEquals(outerType, innerType.ownerType)
        assertEquals(ResolvableType<Int>(), outerType.typeArguments[0].type)
        assertEquals(ResolvableType<String>(), innerType.typeArguments[0].type)
    }

    @Test
    fun testNestedInterface() {
        val outerType = ResolvableType<OuterInterface<Int>>()
        val innerType = ResolvableType<OuterInterface.InnerClass<String>>()

        assertEquals(ResolvableType<Int>(), outerType.typeArguments[0].type)
        assertEquals(ResolvableType<String>(), innerType.typeArguments[0].type)
    }

    interface Foo
    interface FooSon : Foo

    interface Bar<in T : Foo>
    interface Baz<T : Foo>

    @Test
    fun testAssignCheck() {
        val nullableString = ResolvableType<String?>()
        val string = ResolvableType<String>()

        assertTrue(nullableString.isAssignableFrom(string))
        assertFalse(string.isAssignableFrom(nullableString))

        val foo = ResolvableType<Foo>()
        val fooSon = ResolvableType<FooSon>()

        assertTrue(foo.isAssignableFrom(fooSon))
        assertFalse(fooSon.isAssignableFrom(foo))

        val bar = ResolvableType<Bar<Foo>>()
        val barSon = ResolvableType<Bar<FooSon>>()

        assertTrue(bar.isAssignableFrom(barSon))
        assertFalse(barSon.isAssignableFrom(bar))

        val baz = ResolvableType<Baz<Foo>>()
        val bazSon = ResolvableType<Baz<FooSon>>()

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
        val foo4 = ResolvableType<Foo1.Foo2.Foo3.Foo4<Float>>()
        val foo3 = ResolvableType<Foo1.Foo2.Foo3<Float>>()
        val foo2 = ResolvableType<Foo1.Foo2<Float>>()
        val foo1 = ResolvableType<Foo1<Float>>()
    }

    @Test
    fun testSuperClassParameter() {
        val string = ResolvableType<String>()
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
        val foo3 = ResolvableType<Foo3<Float>.Foo3.Foo3<Double, String>>()
        val innerFoo3 = foo3.memberTypes.single()

        val fooFun = innerFoo3.memberFunctions.single { it.rawFunction.name == "foo" }
        val fooFunT = fooFun.parameters.last()
        assertEquals(ResolvableType<Map<Map<String, List<Map<Double, String>>>, String>>(), fooFunT.type)

        val fooFunReturnType = fooFun.returnType!!
        assertEquals(ResolvableType<Double>(), fooFunReturnType.getTypeArgument<Pair<*, *>>(0)?.type)

        assertEquals(
            ResolvableType<String>(),
            fooFunReturnType.getTypeArgument<Pair<*, *>>(1)?.type?.getTypeArgument<Map<*, *>>("K")?.type
        )
    }

    class Foo4 {
        val field: Int = error("Not implemented")
    }

    @Test
    fun testField() {
        val foo4 = ResolvableType<Foo4>()
        val field = foo4.memberProperties.singleOrNull { it.name == "field" }
    }

    class Foo5<T> {
        val field: T = error("Not implemented")
    }

    @Test
    fun testParameterizedField() {
        val foo5 = ResolvableType<Foo5<Int>>()
        val field = foo5.memberProperties.singleOrNull { it.name == "field" }

        assertEquals(ResolvableType<Int>(), field?.type)
    }

    @Test
    fun testMutableMap() {
        val mutableMap = ResolvableType<MutableMap<String, String>>()

        val typeResolver = TypeResolver()
        val putFun = mutableMap.rawClass.java.methods.single { it.name == "put" }
        val putReturnType = typeResolver.resolveByOuterType(mutableMap, putFun.genericReturnType)

        assertEquals(ResolvableType<String>(), putReturnType)
    }

    @Test
    fun testComplexMutableMap() {
        val mutableMap = ResolvableType<MutableMap<String, Map<List<Pair<Float, URI>>, Int>>>()

        val typeResolver = TypeResolver()
        val putFun = mutableMap.rawClass.java.methods.single { it.name == "put" }
        val putReturnType = typeResolver.resolveByOuterType(mutableMap, putFun.genericReturnType)

        assertEquals(ResolvableType<Map<List<Pair<Float, URI>>, Int>>(), putReturnType)
    }
}
