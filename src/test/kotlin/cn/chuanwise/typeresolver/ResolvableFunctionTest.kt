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
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.kotlinFunction

private fun foo(): Nothing = error("Not implemented")
private fun <T> bar(): T & Any = error("Not implemented")

class ResolvableFunctionTest {
    class Foo<T> {
        inner class Bar {
            fun foo(t: List<T>): T = error("Not implemented")
        }
    }

    @Test
    fun testFunctionTypeArgument() {
        val map = ResolvableType<Map<String, Int>>()

        val rawGetFunction = map.rawClass.java.getDeclaredMethod("get", Any::class.java).kotlinFunction
        val getFunction = map.memberFunctionsByRawFunction[rawGetFunction]!!

        assertEquals(ResolvableType<Int>(), getFunction.returnType)

        val bar = ResolvableType<Foo<String>.Bar>()
        val fooFunction = bar.rawClass.java.getDeclaredMethod("foo", List::class.java).kotlinFunction
        val foo = bar.memberFunctionsByRawFunction[fooFunction]!!

        assertEquals(ResolvableType<String>(), foo.returnType)

        val fooT = foo.parameters.last().type!!
        assertEquals(ResolvableType<List<String>>(), fooT)
    }

    class Foo2<T> {
        inner class Bar2<T> {
            fun foo(t: List<T>): T = error("Not implemented")
        }
    }

    @Test
    fun testComplexFunctionTypeArgument() {
        val bar = ResolvableType<Foo2<String>.Bar2<Int>>()
        val fooFunction = bar.rawClass.java.getDeclaredMethod("foo", List::class.java).kotlinFunction
        val foo = bar.memberFunctionsByRawFunction[fooFunction]!!

        assertEquals(ResolvableType<Int>(), foo.returnType)
        assertEquals(ResolvableType<List<Int>>(), foo.parameters.last().type!!)
    }

    @Test
    fun testOuterFunction() {
        val foo = ResolvableFunction(::foo)
        val bar = ResolvableFunction<Any>(::bar)
    }
}