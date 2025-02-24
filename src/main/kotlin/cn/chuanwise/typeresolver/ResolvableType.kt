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
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberFunctions

/**
 * 用于解析 [KType] 的工具，
 *
 * @param T
 * @author Chuanwise
 * @see createTypeResolver
 */
interface ResolvableType<T> : ResolvableTypeArgumentOwner {
    val rawType: KType
    val rawClass: KClass<T & Any>

    val isNullable: Boolean

    val superTypes: List<ResolvableType<*>>

    val outerType: ResolvableType<*>?
    val innerTypes: List<ResolvableType<*>>

    val isInnerClass: Boolean
    val isMemberClass: Boolean

    val memberFunctions: List<ResolvableFunction<*>>
    val memberFunctionsByRawFunction: Map<KFunction<*>, ResolvableFunction<*>>

    fun isAssignableFrom(other: ResolvableType<*>): Boolean
    fun isAssignableTo(other: ResolvableType<*>): Boolean

    fun getTypeArgument(rawClass: KClass<*>, name: String) : ResolvableTypeArgument?
    fun getTypeArgumentOrFail(rawClass: KClass<*>, name: String) : ResolvableTypeArgument {
        return getTypeArgument(rawClass, name) ?: throw IllegalArgumentException("No type argument named $name for raw class $rawClass.")
    }

    fun getTypeArgument(rawClass: KClass<*>, index: Int) : ResolvableTypeArgument?
    fun getTypeArgumentOrFail(rawClass: KClass<*>, index: Int) : ResolvableTypeArgument {
        return getTypeArgument(rawClass, index) ?: throw IllegalArgumentException("No type argument at index $index for raw class $rawClass.")
    }
}

private interface PromptSupported {
    fun prompt(name: String, type: KType, subType: ResolvableTypeImpl<*>, resolver: TypeResolver): Boolean
}

internal class ResolvableTypeImpl<T>(
    override var rawType: KType,
    override val rawClass: KClass<T & Any>,
    override val outerType: ResolvableType<*>?,
    builder: ResolvableTypeBuilderImpl<T>
) : ResolvableType<T>, PromptSupported {
    override val typeParameters: List<KTypeParameter> = rawClass.typeParameters
    override val typeParametersByName: Map<String, KTypeParameter> = typeParameters.associateBy { it.name }

    override val isNullable: Boolean = rawType.isMarkedNullable
    override var isResolved: Boolean = false

    override val superTypes: List<ResolvableTypeImpl<*>>
    override val innerTypes: List<ResolvableTypeImpl<*>> = mutableListOf()

    private var typeArgumentsSet = false

    override val typeArguments: List<AbstractResolvableTypeArgument>
    override val typeArgumentsByName: Map<String, ResolvableTypeArgument>

    private val memberFunctionDelegate: Lazy<List<ResolvableFunction<*>>>
    override val memberFunctions: List<ResolvableFunction<*>> get() = memberFunctionDelegate.value
    override val memberFunctionsByRawFunction: Map<KFunction<*>, ResolvableFunction<*>> by lazy {
        memberFunctions.associateBy { it.rawFunction }
    }

    override val isInnerClass: Boolean
    override val isMemberClass: Boolean

    private fun flush() {
        isResolved = outerType?.isResolved != false && typeArguments.all { it.isResolved }
    }

    // 尝试用子类提供的新线索看看能否对解析当前类型提供帮助。返回 true 表示有帮助。
    override fun prompt(name: String, type: KType, subType: ResolvableTypeImpl<*>, resolver: TypeResolver) : Boolean {
        if (isResolved || !typeArgumentsSet) {
            return false
        }

        var result = false
        for (typeArgument in typeArguments) {
            typeArgument as? ResolvableTypeArgumentImpl ?: continue
            result = typeArgument.prompt(name, type, subType, resolver) || result

            for (superType in superTypes) {
                result = superType.prompt(typeArgument.name, typeArgument.rawType, this, resolver) || result
            }
        }
        if (result) {
            flush()
            if (isResolved) {
                val unresolvedTypeArguments = rawType.arguments.filter { it.type?.classifier is KTypeParameter }
                if (unresolvedTypeArguments.isNotEmpty()) {
                    val argumentTypes = mutableListOf<KType?>()
                    for (resolvableType in generateSequence(this as ResolvableType<*>) { it.outerType }) {
                        argumentTypes.addAll(resolvableType.typeArguments.map { it.type?.rawType })
                        if (!resolvableType.isInnerClass) {
                            break
                        }
                    }

                    check(argumentTypes.size == unresolvedTypeArguments.size) {
                        "The number of unresolved type arguments is not equal to the number of resolved type arguments."
                    }
                    val arguments = unresolvedTypeArguments.zip(argumentTypes).map { (argument, type) ->
                        KTypeProjection(argument.variance, type)
                    }

                    rawType = rawClass.createType(arguments, isNullable, emptyList())
                }
            }
        }
        return result
    }

    abstract class AbstractResolvableTypeArgumentBound : ResolvableTypeArgumentBound, PromptSupported

    private class ResolvableTypeArgumentBoundImpl(
        override val type: ResolvableTypeImpl<*>
    ) : AbstractResolvableTypeArgumentBound(), PromptSupported by type {
        override val isResolved: Boolean get() = type.isResolved
    }

    private class LazyResolvableTypeArgumentBoundImpl(
        private val name: String
    ) : AbstractResolvableTypeArgumentBound() {
        private var argument: AbstractResolvableTypeArgument? = null

        override val type: ResolvableType<*>? get() = argument?.type
        override val isResolved: Boolean get() = argument?.isResolved == true

        override fun prompt(
            name: String,
            type: KType,
            subType: ResolvableTypeImpl<*>,
            resolver: TypeResolver
        ): Boolean {
            if (!isResolved) {
                return false
            }

            argument = subType.getTypeArgument(this.name) as? AbstractResolvableTypeArgument
            return argument?.prompt(name, type, subType, resolver) == true
        }
    }

    abstract class AbstractResolvableTypeArgument : ResolvableTypeArgument, PromptSupported

    private class AllResolvableTypeArgumentImpl(
        override val name: String,
        override val rawParameter: KTypeParameter,
        override val rawProjection: KTypeProjection
    ) : AbstractResolvableTypeArgument() {
        override val isAll: Boolean = true
        override val isResolved: Boolean = true

        override val type: ResolvableType<*>? = null
        override val bounds: List<ResolvableTypeArgumentBound> = emptyList()

        override fun toString(): String = "*"

        override fun prompt(name: String, type: KType, subType: ResolvableTypeImpl<*>, resolver: TypeResolver): Boolean {
            return false
        }
    }

    private class ResolvableTypeArgumentImpl(
        override val name: String,
        override val rawParameter: KTypeParameter,
        override val rawProjection: KTypeProjection,
        override val bounds: List<AbstractResolvableTypeArgumentBound>,
        var rawType: KType,
        override var type: ResolvableType<*>?
    ) : AbstractResolvableTypeArgument() {
        override val isAll: Boolean = false
        override var isResolved: Boolean = type?.isResolved == true

        override fun prompt(name: String, type: KType, subType: ResolvableTypeImpl<*>, resolver: TypeResolver): Boolean {
            if (isResolved) {
                return false
            }
            var result = false

            val classifier = rawType.classifier
            if (classifier is KTypeParameter && classifier.name == name) {
                rawType = type
                this.type = resolver.resolve(type)
                result = true
            }

            for (bound in bounds) {
                result = bound.prompt(name, type, subType, resolver) || result
            }

            if (result) {
                flush()
            }
            return result
        }

        private fun flush() {
            isResolved = bounds.all { it.isResolved } && type?.isResolved == true
        }
    }

    private class BuilderTypeResolverImpl(
        private val builder: ResolvableTypeBuilderImpl<*>
    ) : TypeResolver {
        override fun resolve(type: KType): ResolvableType<*> = builder.getCacheTypeOrResolve(type)
    }

    init {
        builder.addTypeCache(this)
        val builderResolver = BuilderTypeResolverImpl(builder)

        isMemberClass = outerType != null
        isInnerClass = isMemberClass && rawClass.isInner

        superTypes = rawClass.supertypes.map { builder.getCacheTypeOrResolve(it) }

        val rawTypeParameters = rawClass.typeParameters
        val rawTypeArguments = rawType.arguments

        if (rawTypeParameters.isEmpty()) {
            typeArguments = emptyList()
            flush()
        } else {
            val typeArguments: MutableList<AbstractResolvableTypeArgument> = mutableListOf()
            this.typeArguments = typeArguments

            for (i in rawTypeParameters.indices) {
                val rawTypeParameter = rawTypeParameters[i]
                val rawTypeArgument = rawTypeArguments[i]

                val rawTypeArgumentType = rawTypeArgument.type
                val rawTypeArgumentVariance = rawTypeArgument.variance

                val typeArgument: AbstractResolvableTypeArgument = if (rawTypeArgumentType == null || rawTypeArgumentVariance == null) {
                    AllResolvableTypeArgumentImpl(rawTypeParameter.name, rawTypeParameter, rawTypeArgument)
                } else {
                    val argumentBounds: List<AbstractResolvableTypeArgumentBound>
                    val argumentType: ResolvableType<*>?

                    when (val classifier = rawTypeArgumentType.classifier) {
                        is KClass<*> -> {
                            for (superType in superTypes) {
                                superType.prompt(rawTypeParameter.name, rawTypeArgumentType, this, builderResolver)
                            }

                            argumentBounds = emptyList()
                            argumentType = builder.getCacheTypeOrResolve(rawTypeArgumentType)
                        }
                        is KTypeParameter -> {
                            argumentBounds = classifier.upperBounds.map {
                                when (val boundClassifier = it.classifier) {
                                    is KClass<*> -> ResolvableTypeArgumentBoundImpl(builder.getCacheTypeOrResolve(it))
                                    is KTypeParameter -> LazyResolvableTypeArgumentBoundImpl(boundClassifier.name)
                                    else -> throw IllegalArgumentException(
                                        "Unknown bound classifier for the ${i + 1}-th type argument of $rawClass: $boundClassifier"
                                    )
                                }
                            }
                            argumentType = null
                        }
                        else -> throw IllegalArgumentException("Unknown classifier for the ${i + 1}-th type argument of $rawClass: $classifier")
                    }

                    ResolvableTypeArgumentImpl(
                        rawTypeParameter.name,
                        rawTypeParameter,
                        rawTypeArgument,
                        argumentBounds,
                        rawTypeArgumentType,
                        argumentType
                    )
                }

                typeArguments.add(typeArgument)
            }
        }

        typeArgumentsByName = typeArguments.associateBy { it.name }
        typeArgumentsSet = true

        memberFunctionDelegate = lazy {
            rawClass.memberFunctions.map { ResolvableFunctionImpl(this, it, builderResolver) }
        }

        flush()
    }

    override fun getTypeArgument(rawClass: KClass<*>, index: Int): ResolvableTypeArgument? {
        if (rawClass == this.rawClass) {
            return typeArguments.getOrNull(index)
        }
        for (superType in superTypes) {
            val result = superType.getTypeArgument(rawClass, index)
            if (result != null) {
                return result
            }
        }
        return null
    }

    override fun getTypeArgument(rawClass: KClass<*>, name: String): ResolvableTypeArgument? {
        if (rawClass == this.rawClass) {
            return typeArgumentsByName[name]
        }
        for (superType in superTypes) {
            val result = superType.getTypeArgument(rawClass, name)
            if (result != null) {
                return result
            }
        }
        return null
    }

    override fun isAssignableFrom(other: ResolvableType<*>): Boolean {
        if (this == other) {
            return true
        }
        if (!rawClass.isSuperclassOf(other.rawClass)) {
            return false
        }

        for (i in typeArguments.indices) {
            val thisTypeArgument = typeArguments[i]
            val otherTypeArgument = other.typeArguments[i]

            if (otherTypeArgument.isAll) {
                continue
            }

            val otherTypeArgumentType = otherTypeArgument.type ?: error("The ${i + 1}-th type argument of $other is not resolved. ")
            for (j in thisTypeArgument.bounds.indices) {
                val bound = thisTypeArgument.bounds[j]
                val boundType = bound.type ?: error("The ${j + 1}-th bound of the ${i + 1}-th type argument of $this is not resolved. ")
                if (!boundType.isAssignableFrom(otherTypeArgumentType)) {
                    return false
                }
            }

            val thisTypeArgumentType = thisTypeArgument.type ?: error("The ${i + 1}-th type argument of $this is not resolved. ")
            val result = when (thisTypeArgument.rawParameter.variance) {
                KVariance.IN -> thisTypeArgumentType.isAssignableFrom(otherTypeArgumentType)
                KVariance.OUT -> otherTypeArgumentType.isAssignableFrom(thisTypeArgumentType)
                KVariance.INVARIANT -> thisTypeArgumentType == otherTypeArgumentType
                else -> error("Unknown variance ${thisTypeArgument.rawProjection.variance} for the ${i + 1}-th type argument of $this. ")
            }

            if (!result) {
                return false
            }
        }

        return isNullable || !other.isNullable
    }

    override fun isAssignableTo(other: ResolvableType<*>): Boolean {
        return other.isAssignableFrom(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ResolvableType<*>) {
            return false
        }

        return rawType == other.rawType
    }

    override fun hashCode(): Int {
        return rawType.hashCode()
    }

    override fun toString(): String {
        return rawType.toString()
    }
}