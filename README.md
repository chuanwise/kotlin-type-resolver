# Kotlin Type Resolver - Kotlin 类型解析器

一个用于解析 Kotlin 类型，尤其是泛型类型的工具；支持 Kotlin JVM。

## 速览

### 可解析类型 - `ResolvableType<T>`

使用 `createResolvableType<T>()` 创建一个可解析类型，通过它进行各种操作。

```kt
import kotlin.reflect.typeOf

// 使用 `createResolvableType<T>()` 创建一个可解析类型。
val string = createResolvableType<String>()
assertFalse(string.isNullable)

// 支持空安全类型。
val nullableString = createResolvableType<String?>()
assertTrue(nullableString.isNullable)

// 支持空安全赋值检查。
assertTrue(nullableString.isAssignableFrom(string))
assertFalse(string.isAssignableFrom(nullableString))

// 支持提取泛型参数类型。因为 String 实现 Comparable<String>，故可以由此提取后者的泛型参数。
val comparableT = string.getTypeArgumentOrFail(Comparable::class, "T").type!!
assertEquals(string, comparableT)

// 通过 rawClass 和 rawType 获取 KClass<T & Any> 和 KType
val kClass = string.rawClass
val kType = string.rawType
assertEquals(String::class, kClass)
assertEquals(typeOf<String>(), kType)

// 支持协变逆变等泛型类型的处理。因为 List<in T>，所以 List<String> 可以赋值给 List<Any>。
val stringList = createResolvableType<List<String>>()
val anyList = createResolvableType<List<Any>>()
assertTrue(anyList.isAssignableFrom(stringList))
```

支持嵌套类相关处理：

```kt
class Foo {
    inner class Bar
}

// 通过 outerType 获取外部类的可解析类型，或 `null` 如果没有外部类。
val foo = createResolvableType<Foo>()
val fooBar = createResolvableType<Foo.Bar>()
assertEquals(fooBar, foo.outerType)
```

### 可解析函数 - `ResolvableFunction<T>`

通过 `ResolvableType<*>.memberFunctions` 获取成员函数，或通过 `createResolvableFunction<T>(rawFunction)` 创建一个可解析函数。

```kt
class Foo1<T> {
    inner class Bar1 {
        fun baz1(t: List<T>): T = error("Not implemented")
    }
}

// 还可以进行方法返回值参数推导，并支持根据外部类信息推导。
val bar1 = createResolvableType<Foo1<String>.Bar1>()
val baz1 = bar1.memberFunctions.single { it.rawFunction.name == "baz1" }
assertEquals(createResolvableType<String>(), baz1.returnType)

// 当然方法里的参数也不在话下。
assertEquals(createResolvableType<List<String>>(), baz1.parameters.last().type)

class Foo2<T> {
    class Bar2<T> {
        fun baz2(t: List<T>): T = error("Not implemented")
    }
}

// 哪怕外部类有同名泛型参数，也可以正确推导。
val bar2 = createResolvableType<Foo2<String>.Bar2<Int>>()
val baz2 = bar2.memberFunctions.single { it.rawFunction.name == "baz2" }
assertEquals(createResolvableType<Int>(), baz2.returnType)
assertEquals(createResolvableType<List<Int>>(), baz2.parameters.last().type)
```
