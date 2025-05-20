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

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter

/**
 * Type resolver, actually a type cache. 
 *
 * @author Chuanwise
 */
interface TypeResolver {
    /**
     * Resolve given kotlin type to a [ResolvableType].
     *
     * @param type kotlin type
     * @return a [ResolvableType] that represents the given kotlin type
     */
    fun resolve(type: KType): ResolvableType<*>
}

/**
 * Create a new [TypeResolver] instance.
 *
 * @return a new [TypeResolver] instance
 */
fun TypeResolver(): TypeResolver = TypeResolverImpl()

fun <T> TypeResolver.resolve(rawClass: KClass<T & Any>, nullable: Boolean = false): ResolvableType<T> {
    return ResolvableTypeBuilder<T>(rawClass, this).nullable(nullable).build()
}

inline fun <T> TypeResolver.buildType(
    rawClass: KClass<T & Any>, block: ResolvableTypeBuilder<T>.() -> Unit
): ResolvableType<T> {
    return ResolvableTypeBuilder<T>(rawClass, this).apply(block).build()
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> TypeResolver.buildType(
    block: ResolvableTypeBuilder<T>.() -> Unit
): ResolvableType<T> {
    return buildType(T::class as KClass<T & Any>, block)
}

private fun ResolvableType<*>?.promptByOuterType(type: ResolvableType<*>, typeResolver: TypeResolver) {
    require(type is ResolvableTypeImpl<*>) { "Type must be ResolvableTypeImpl" }

    for (resolvableType in generateSequence(this) { it.ownerType }) {
        if (type.isResolved || type.isInnerClass) {
            break
        }

        for (typeArgument in resolvableType.typeArguments) {
            val typeArgumentType = typeArgument.type ?: continue
            type.prompt(
                typeArgument.name, typeArgumentType.rawType,
                resolvableType as ResolvableTypeImpl<*>, typeResolver
            )

            if (type.isResolved) {
                break
            }
        }
    }
}

private fun ResolvableType<*>?.getTypeArgument(name: String): ResolvableTypeArgument? {
    generateSequence(this) { it.ownerType }.forEach { type ->
        type.typeArgumentsByName[name]?.let { return it }
    }
    return null
}

fun TypeResolver.infer(root: ResolvableType<*>?, type: KType, ignored: Set<String> = emptySet()) : ResolvableType<*>? {
    return when (val classifier = type.classifier) {
        is KTypeParameter -> {
            // 如果是在本函数上定义的类型参数，就无法解析。
            if (classifier.name in ignored) {
                return null
            }

            // 否则在外部类里找这个参数。
            return root.getTypeArgument(classifier.name)?.type
        }
        is KClass<*> -> resolve(type).apply {
            root.promptByOuterType(this, this@infer)
        }
        else -> error("Unsupported type classifier: $classifier")
    }
}

@Suppress("UNCHECKED_CAST")
fun TypeResolver.infer(root: ResolvableType<*>?, type: Type, ignored: Set<String> = emptySet()) : ResolvableType<*>? {
    return when (type) {
        is TypeVariable<*> -> {
            if (type.name in ignored) {
                return null
            }

            var result: ResolvableType<*>? = null
            for (resolvableType in generateSequence(root) { it.ownerType }) {
                result = resolvableType.typeArgumentsByName[type.name]?.type
                if (result != null || !resolvableType.isInnerClass) {
                    break
                }
            }
            result as ResolvableTypeImpl<*>?
        }
        is ParameterizedType -> {
            resolve((type.rawType as Class<*>).kotlin as KClass<Any>).apply {
                root.promptByOuterType(this, this@infer)
            }
        }
        is Class<*> -> {
            resolve(type.kotlin as KClass<Any>).apply {
                root.promptByOuterType(this, this@infer)
            }
        }
        else -> error("Unsupported type: $type")
    }
}

internal class TypeResolverImpl(
    private val cache: MutableMap<KType, ResolvableType<*>> = ConcurrentHashMap<KType, ResolvableType<*>>()
): TypeResolver {
    internal fun getTypeCache(type: KType): ResolvableType<*>? {
        return cache[type]
    }

    internal fun addTypeCache(type: ResolvableType<*>) {
        cache[type.rawType] = type
    }

    override fun resolve(type: KType): ResolvableType<*> {
        cache[type]?.let { return it }

        return ResolvableTypeBuilder(type, this)
            .build()
            .apply {
                if (isResolved) {
                    cache[type] = this
                }
            }
    }
}