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
 * 可解析类型，用于解析 [KType] 的工具，也可以用于推导类型参数。
 *
 * 通过 [createResolvableType] 或 [TypeResolver.resolve] 构造。无法构造无法表示的类型，例如 `T & Any`。
 *
 * @param T 类型的原始类型。
 * @author Chuanwise
 * @see createTypeResolver
 */
interface ResolvableType<T> : ResolvableTypeArgumentOwner {
    /**
     * 该类型的原始类型。
     */
    val rawType: KType

    /**
     * 该类型的原始类。
     */
    val rawClass: KClass<T & Any>

    /**
     * 该类型是否可控。
     */
    val isNullable: Boolean

    /**
     * 类型的父类，或实现的接口。
     */
    val superTypes: List<ResolvableType<*>>

    /**
     * 类型的外部类，如果有。
     */
    val outerType: ResolvableType<*>?

    /**
     * 类型的成员类。
     */
    val memberTypes: List<ResolvableType<*>>

    /**
     * 通过原始类获取成员类。
     */
    val memberTypesByRawClass: Map<KClass<*>, ResolvableType<*>>

    /**
     * 类型是否是内部类，其持有外部类的引用。
     */
    val isInnerClass: Boolean

    /**
     * 类型是否是成员类型，不一定持有外部类的引用，可能只是在其内定义。
     */
    val isMemberClass: Boolean

    /**
     * 类型的成员函数。
     */
    val memberFunctions: List<ResolvableFunction<*>>

    /**
     * 通过原始函数获取成员函数。
     */
    val memberFunctionsByRawFunction: Map<KFunction<*>, ResolvableFunction<*>>

    /**
     * 检查当前类型的引用能否被 [other] 类型的引用赋值。
     *
     * @param other 另一个类型。
     * @return 如果当前类型的引用能被 [other] 类型的引用赋值，则返回 `true`。
     */
    fun isAssignableFrom(other: ResolvableType<*>): Boolean

    /**
     * 检查当前类型的引用能否赋值给 [other] 类型的引用。
     *
     * @param other 另一个类型。
     * @return 如果当前类型的引用能赋值给 [other] 类型的引用，则返回 `true`。
     */
    fun isAssignableTo(other: ResolvableType<*>): Boolean

    /**
     * 获取当前类型在 [rawClass] 定义的泛型参数 [name] 的值。
     *
     * @param rawClass 泛型参数所在的类。
     * @param name 泛型参数的名称。
     * @return 如果找到了泛型参数，则返回泛型参数的值，否则返回 `null`。
     */
    fun getTypeArgument(rawClass: KClass<*>, name: String) : ResolvableTypeArgument?

    /**
     * 获取当前类型在 [rawClass] 定义的泛型参数 [name] 的值，如果找不到则抛出异常。
     *
     * @param rawClass 泛型参数所在的类。
     * @param name 泛型参数的名称。
     * @return 泛型参数的值。
     * @throws IllegalArgumentException 如果找不到泛型参数。
     */
    fun getTypeArgumentOrFail(rawClass: KClass<*>, name: String) : ResolvableTypeArgument {
        return getTypeArgument(rawClass, name) ?: throw IllegalArgumentException("No type argument named $name for raw class $rawClass.")
    }

    /**
     * 获取当前类型在 [rawClass] 定义的泛型参数 [index] 的值。
     *
     * @param rawClass 泛型参数所在的类。
     * @param index 泛型参数的索引。
     * @return 如果找到了泛型参数，则返回泛型参数的值，否则返回 `null`。
     */
    fun getTypeArgument(rawClass: KClass<*>, index: Int) : ResolvableTypeArgument?

    /**
     * 获取当前类型在 [rawClass] 定义的泛型参数 [index] 的值，如果找不到则抛出异常。
     *
     * @param rawClass 泛型参数所在的类。
     * @param index 泛型参数的索引。
     * @return 泛型参数的值。
     * @throws IllegalArgumentException 如果找不到泛型参数。
     */
    fun getTypeArgumentOrFail(rawClass: KClass<*>, index: Int) : ResolvableTypeArgument {
        return getTypeArgument(rawClass, index) ?: throw IllegalArgumentException("No type argument at index $index for raw class $rawClass.")
    }
}

// 允许使用相关类型的类型参数完善自身信息的接口。
// 例如当前函数的返回类型是 T，但 T 由外部类定义，只能在外部类解析出 T 的实际类型后，
// 调用内部类的 prompt 方法，将 T 的实际类型传递给内部类。返回 true 表示有帮助。
private interface PromptSupported {
    fun prompt(name: String, rawType: KType, type: ResolvableTypeImpl<*>, resolver: TypeResolver): Boolean
}

@Suppress("UNCHECKED_CAST")
internal class ResolvableTypeImpl<T>(
    override var rawType: KType,
    override val rawClass: KClass<T & Any>,
    override val outerType: ResolvableType<*>?,

    // 需要 builder 的原因是需要把初始化到一半的类型缓存进去。
    builder: ResolvableTypeBuilderImpl<T>
) : ResolvableType<T>, PromptSupported {
    override val typeParameters: List<KTypeParameter> = rawClass.typeParameters
    override val typeParametersByName: Map<String, KTypeParameter> = typeParameters.associateBy { it.name }

    override val isNullable: Boolean = rawType.isMarkedNullable
    override var isResolved: Boolean = false

    override val superTypes: List<ResolvableTypeImpl<*>>

    private val memberTypesDelegate: Lazy<List<ResolvableTypeImpl<*>>>
    override val memberTypes: List<ResolvableTypeImpl<*>> get() = memberTypesDelegate.value
    override val memberTypesByRawClass: Map<KClass<*>, ResolvableType<*>> by lazy {
        memberTypes.associateBy { it.rawClass }
    }

    override val typeArguments: List<AbstractResolvableTypeArgument>
    override val typeArgumentsByName: Map<String, ResolvableTypeArgument>
    private var initialized = false

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

    override fun prompt(name: String, rawType: KType, type: ResolvableTypeImpl<*>, resolver: TypeResolver) : Boolean {
        if (isResolved || !initialized) {
            return false
        }

        var result = false
        for (typeArgument in typeArguments) {
            typeArgument as? ResolvableTypeArgumentImpl ?: continue
            result = typeArgument.prompt(name, rawType, type, resolver) || result

            for (superType in superTypes) {
                result = superType.prompt(typeArgument.name, typeArgument.rawType, this, resolver) || result
            }
        }
        if (result) {
            flush()

            // 刷新的时候重构一下类型。
            val unresolvedTypeArguments = this.rawType.arguments
            if (unresolvedTypeArguments.isNotEmpty()) {
                val argumentTypes = mutableListOf<KType?>()
                for (resolvableType in generateSequence(this as ResolvableType<*>) { it.outerType }) {
                    argumentTypes.addAll(resolvableType.typeArguments.map { it.type?.rawType ?: it.rawProjection.type })
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

                this.rawType = rawClass.createType(arguments, isNullable, emptyList())
            }
        }
        return result
    }

    abstract class AbstractResolvableTypeArgumentBound : ResolvableTypeArgumentBound, PromptSupported

    // 能构造出对应 ResolvableType<*> 的泛型参数范围，但是不一定此类型已经解析。
    private class ResolvableTypeArgumentBoundImpl(
        override val type: ResolvableTypeImpl<*>
    ) : AbstractResolvableTypeArgumentBound(), PromptSupported by type {
        override val isResolved: Boolean get() = type.isResolved
    }

    // 暂时无法构造出对应 ResolvableType<*> 的泛型参数范围，需要等到解析外部类或子类时才能构造。
    private class LazyResolvableTypeArgumentBoundImpl(
        private val name: String
    ) : AbstractResolvableTypeArgumentBound() {
        private var argument: AbstractResolvableTypeArgument? = null

        override val type: ResolvableType<*>? get() = argument?.type
        override val isResolved: Boolean get() = argument?.isResolved == true

        override fun prompt(
            name: String,
            rawType: KType,
            type: ResolvableTypeImpl<*>,
            resolver: TypeResolver
        ): Boolean {
            if (!isResolved) {
                return false
            }

            argument = type.typeArgumentsByName[this.name] as? AbstractResolvableTypeArgument
            return argument?.prompt(name, rawType, type, resolver) == true
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

        override fun prompt(name: String, rawType: KType, type: ResolvableTypeImpl<*>, resolver: TypeResolver): Boolean {
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

        private fun KType.prompt(name: String, rawType: KType, type: ResolvableTypeImpl<*>, resolver: TypeResolver) : KType {
            var result = this

            // 检查当前参数是否是现在的 T。如果是的话替换为当前类型。
            val classifier = result.classifier
            if (classifier is KTypeParameter && classifier.name == name) {
                result = rawType
            }

            // 检查当前参数的泛型参数里是否包含 T。
            var typeUpdated = false
            val newArguments = result.arguments.map {
                // 只处理非 * 的类型。
                val itType = it.type ?: return@map it
                val prompted = itType.prompt(name, rawType, type, resolver)

                if (it.type == prompted) {
                    it
                } else {
                    typeUpdated = true
                    KTypeProjection(it.variance, prompted)
                }
            }

            if (typeUpdated) {
                result = (result.classifier as KClass<*>).createType(newArguments, result.isMarkedNullable, result.annotations)
            }

            return result
        }

        override fun prompt(name: String, rawType: KType, type: ResolvableTypeImpl<*>, resolver: TypeResolver): Boolean {
            if (isResolved) {
                return false
            }
            var result = false

            val newRawType = this.rawType.prompt(name, rawType, type, resolver)
            if (newRawType != this.rawType) {
                this.rawType = newRawType
                this.type = resolver.resolve(newRawType)
                result = true
            }

            for (bound in bounds) {
                result = bound.prompt(name, rawType, type, resolver) || result
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
        memberTypesDelegate = lazy {
            rawClass.nestedClasses.map {
                createResolvableTypeBuilder(it as KClass<T & Any>, builder.typeResolver)
                    .outerType(this)
                    .build() as ResolvableTypeImpl<*>
            }
        }

        memberFunctionDelegate = lazy {
            rawClass.memberFunctions.map { ResolvableFunctionImpl(this, it, builderResolver) }
        }

        val rawTypeParameters = rawClass.typeParameters
        val rawTypeArguments = rawType.arguments

        if (rawTypeParameters.isEmpty()) {
            typeArguments = emptyList()
            typeArgumentsByName = emptyMap()
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
            typeArgumentsByName = typeArguments.associateBy { it.name }
        }

        initialized = true
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