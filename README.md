# Kotlin Type Resolver

Tool for resolving Kotlin types, especially generic types; supports Kotlin JVM.

## Installation

Add following dependency to your `build.gradle.kts`:

```kts
repositories {
    maven("https://nexus.chuanwise.cn/repository/maven-public/")
}

dependencies {
    implementation("cn.chuanwise:kotlin-type-resolver:0.1.0-SNAPSHOT")
}
```

## Getting Started

### `ResolvableType<T>`

It can be used to resolve generic types, including nested classes and functions.

```kotlin
// Use `ResolvableType<T>` to resolve generic given type.
val string = ResolvableType<String>()
assertFalse(string.isNullable)

// Nullable types are supported.
val nullableString = ResolvableType<String?>()
assertTrue(nullableString.isNullable)

// Nullable type is assignable from non-nullable type, and not vice versa.
assertTrue(nullableString.isAssignableFrom(string))
assertFalse(string.isAssignableFrom(nullableString))

// Extract type arguments from the type. 
// Because String implements Comparable<String>, we can extract the latter's type argument.
val comparableT = string.getTypeArgumentOrFail(Comparable::class, "T").type!!
assertEquals(string, comparableT)

// Get raw class and raw type.
val kClass = string.rawClass
val kType = string.rawType
assertEquals(String::class, kClass)
assertEquals(typeOf<String>(), kType)

// Handle covariance and contravariance of generic types. Because List<in T>, List<String> can be assigned to List<Any>.
val stringList = ResolvableType<List<String>>()
val anyList = ResolvableType<List<Any>>()
assertTrue(anyList.isAssignableFrom(stringList))
```

Nested classes are also supported.

```kotlin
class Foo {
    inner class Bar
}

// Get the outer class's resolvable type through `outerType`, or `null` if no outer class.
val foo = ResolvableType<Foo>()
val fooBar = ResolvableType<Foo.Bar>()
assertEquals(fooBar, foo.outerType)
```

### `ResolvableFunction<T>`

Get member functions through `ResolvableType<*>.memberFunctions`, or create a resolvable function through `ResolvableFunction<T>(rawFunction)`.

```kotlin
class Foo1<T> {
    inner class Bar1 {
        fun baz1(t: List<T>): T = error("Not implemented")
    }
}

// Generic type parameters can be inferred from the outer class.
val bar1 = ResolvableType<Foo1<String>.Bar1>()
val baz1 = bar1.memberFunctions.single { it.rawFunction.name == "baz1" }
assertEquals(ResolvableType<String>(), baz1.returnType)
assertEquals(ResolvableType<List<String>>(), baz1.parameters.last().type)

class Foo2<T> {
    class Bar2<T> {
        fun baz2(t: List<T>): T = error("Not implemented")
    }
}

// Multiple generic parameters with the same name are supported.
val bar2 = ResolvableType<Foo2<String>.Bar2<Int>>()
val baz2 = bar2.memberFunctions.single { it.rawFunction.name == "baz2" }
assertEquals(ResolvableType<Int>(), baz2.returnType)
assertEquals(ResolvableType<List<Int>>(), baz2.parameters.last().type)

// If the type is more complex, such as containing multiple nesting, it can also be inferred correctly.
class Foo3<T> {
    inner class Foo3 {
        inner class Foo3<T, U> {  // foo3
            inner class Foo3 {    // inner foo3
                fun <G> foo(t: Map<Map<U, List<Map<T, String>>>, U>): Pair<T, Map<U, G>> = error("Not implemented")
            }
        }
    }
}

val foo3 = ResolvableType<Foo3<Float>.Foo3.Foo3<Double, String>>()
val innerFoo3 = foo3.memberTypes.single()

val fooFun = innerFoo3.memberFunctions.single { it.rawFunction.name == "foo" }
val fooFunT = fooFun.parameters.last()
assertEquals(ResolvableType<Map<Map<String, List<Map<Double, String>>>, String>>(), fooFunT.type)

// Because the generic parameter G of the function foo cannot be inferred, but we can still get other parts of the type.
val fooFunReturnType = fooFun.returnType    // Pair<T, Map<U, G>>, where G is unknown
assertEquals(ResolvableType<Double>(), fooFunReturnType?.getTypeArgument<Pair<*, *>>(0)?.type)

// Get the result type of the second generic parameter of Pair, which is Map<U, G>, where U is String and G is unknown.
assertEquals(
    ResolvableType<String>(),
    fooFunReturnType?.getTypeArgument<Pair<*, *>>(1)?.type?.getTypeArgument<Map<*, *>>("K")?.type
)
```
