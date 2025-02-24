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

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection

interface ResolvableFunction<T> : ResolvableTypeArgumentOwner {
    /**
     * 如果该函数在类中定义，则返回类类型。
     */
    val ownerType: ResolvableType<*>?

    /**
     * 该函数的返回类型，可能未知，因为也可能是一个方法的泛型参数。
     */
    val returnType: ResolvableType<*>?

    /**
     * 该函数的原始返回类型。
     */
    val rawReturnType: KType

    /**
     * 该函数的原始函数。
     */
    val rawFunction: KFunction<T>

    /**
     * 该函数的参数。
     */
    val parameters: List<ResolvableFunctionParameter>

    /**
     * 该函数的类型参数。
     */
    val parametersByName: Map<String, ResolvableFunctionParameter>
}

internal class ResolvableFunctionImpl<T>(
    override val ownerType: ResolvableType<*>?,
    override val rawFunction: KFunction<T>,
    typeResolver: TypeResolver
) : ResolvableFunction<T> {
    override val typeParameters: List<KTypeParameter> = rawFunction.typeParameters
    override val typeParametersByName: Map<String, KTypeParameter> = typeParameters.associateBy { it.name }

    override val typeArguments: List<ResolvableTypeArgument>
    override val typeArgumentsByName: Map<String, ResolvableTypeArgument>

    override val returnType: ResolvableType<*>?
    override val parameters: List<ResolvableFunctionParameter>

    private var parametersByNameOrNull: Map<String, ResolvableFunctionParameter>? = null
    override val parametersByName: Map<String, ResolvableFunctionParameter> get() = parametersByNameOrNull ?: error("Parameters have no name.")

    override val rawReturnType: KType

    override var isResolved: Boolean = false

    abstract class AbstractResolvableTypeArgument : ResolvableTypeArgument {
        override val rawProjection: KTypeProjection = KTypeProjection.STAR
    }

    private class NotYetResolvedResolvableTypeArgument(
        override val name: String,
        override val rawParameter: KTypeParameter,
    ) : AbstractResolvableTypeArgument() {
        override val isAll: Boolean = false
        override val type: ResolvableType<*>? = null
        override val bounds: List<ResolvableTypeArgumentBound> = emptyList()
        override val isResolved: Boolean = false
    }

    private inner class ResolvableTypeArgumentImpl(
        override val name: String,
        override val rawParameter: KTypeParameter,
    ) : AbstractResolvableTypeArgument() {
        private lateinit var typeArgument: ResolvableTypeArgument

        override val isAll: Boolean = false
        override val isResolved: Boolean get() = typeArgument.isResolved

        init {
            var typeArgument: ResolvableTypeArgument? = null
            var currentType = this@ResolvableFunctionImpl.ownerType
            while (currentType != null) {
                typeArgument = currentType.getTypeArgument(name)
                if (typeArgument != null) {
                    this.typeArgument = typeArgument
                    break
                }
                currentType = currentType.outerType
            }

            requireNotNull(typeArgument) { "No type argument named $name." }
        }

        override val type: ResolvableType<*>? = typeArgument.type
        override val bounds: List<ResolvableTypeArgumentBound> = typeArgument.bounds
    }

    private class ResolvableFunctionParameterImpl(
        override val name: String?,
        override val index: Int,
        override val rawParameter: KParameter,
        override val type: ResolvableType<*>?
    ) : ResolvableFunctionParameter {
        override val isResolved: Boolean get() = type?.isResolved == true
    }

    private fun flush() {
        isResolved = (ownerType?.isResolved != false) && typeArguments.all { it.isResolved }
    }

    init {
        typeArguments = if (typeParameters.isEmpty()) {
            emptyList()
        } else {
            val rawTypeParametersByName = typeParameters.associateBy { it.name }

            typeParameters.map {
                if (it.name in rawTypeParametersByName) {
                    NotYetResolvedResolvableTypeArgument(it.name, it)
                } else {
                    ResolvableTypeArgumentImpl(it.name, it)
                }
            }
        }

        typeArgumentsByName = typeArguments.associateBy { it.name }

        rawReturnType = rawFunction.returnType
        returnType = typeResolver.resolveByOuterType(rawReturnType)

        parameters = rawFunction.parameters.map {
            ResolvableFunctionParameterImpl(it.name, it.index, it, typeResolver.resolveByOuterType(it.type))
        }

        if (parameters.isEmpty()) {
            parametersByNameOrNull = emptyMap()
        } else if (parameters[0].name != null) {
            parametersByNameOrNull = parameters.associateBy { it.name!! }
        }

        flush()
    }

    private fun TypeResolver.resolveByOuterType(type: KType) : ResolvableType<*>? {
        return when (val classifier = type.classifier) {
            is KTypeParameter -> {
                // 如果是在本函数上定义的类型参数，就无法解析。
                if (classifier.name in typeArgumentsByName) {
                    return null
                }

                // 否则在外部类里找这个参数。
                var result: ResolvableType<*>? = null
                for (resolvableType in generateSequence(ownerType) { it.outerType }) {
                    result = resolvableType.getTypeArgument(classifier.name)?.type
                    if (result != null || !resolvableType.isInnerClass) {
                        break
                    }
                }
                result as ResolvableTypeImpl<*>
            }
            is KClass<*> -> {
                val result = resolve(type) as ResolvableTypeImpl<*>
                for (resolvableType in generateSequence(ownerType) { it.outerType }) {
                    if (result.isResolved || result.isInnerClass) {
                        break
                    }

                    for (typeArgument in resolvableType.typeArguments) {
                        val typeArgumentType = typeArgument.type ?: continue
                        result.prompt(
                            typeArgument.name, typeArgumentType.rawType,
                            resolvableType as ResolvableTypeImpl<*>, this
                        )
                        if (result.isResolved) {
                            break
                        }
                    }
                }
                result
            }
            else -> error("Unsupported type classifier: $classifier")
        }
    }
}