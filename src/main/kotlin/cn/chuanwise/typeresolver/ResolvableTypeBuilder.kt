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

import java.lang.reflect.TypeVariable
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

/**
 * [createResolvableType] 构建器。
 *
 *
 * @author Chuanwise
 */
interface ResolvableTypeBuilder<T> {
    val typeResolver: TypeResolver?

    fun nullable(nullable: Boolean): ResolvableTypeBuilder<T>
    fun nullable(): ResolvableTypeBuilder<T> = apply { nullable(nullable = true) }
    fun notNullable(): ResolvableTypeBuilder<T> = apply { nullable(nullable = false) }

    fun outerType(type: ResolvableType<*>): ResolvableTypeBuilder<T>

    fun typeArgument(name: String, type: ResolvableType<*>, variance: KVariance = KVariance.INVARIANT): ResolvableTypeBuilder<T>
    fun typeArgument(index: Int, type: ResolvableType<*>, variance: KVariance = KVariance.INVARIANT): ResolvableTypeBuilder<T>
    fun typeArgument(type: ResolvableType<*>, variance: KVariance = KVariance.INVARIANT): ResolvableTypeBuilder<T>

    fun typeArgument(name: String, typeProjection: KTypeProjection): ResolvableTypeBuilder<T>
    fun typeArgument(index: Int, typeProjection: KTypeProjection): ResolvableTypeBuilder<T>
    fun typeArgument(typeProjection: KTypeProjection): ResolvableTypeBuilder<T>

    fun annotation(annotation: Annotation): ResolvableTypeBuilder<T>

    fun build(): ResolvableType<T>
}

@Suppress("UNCHECKED_CAST")
internal class ResolvableTypeBuilderImpl<T>(
    override val typeResolver: TypeResolverImpl?,
    private val rawType: KType? = null,
    initialRawClass: KClass<T & Any>? = null,

    // 用于存储构建中途产生的类型。例如，欲构建 String，需解析其实现的接口 Comparable<String>，
    // 进而又需要解析 String。此时需要获取构建到一半的 ResolvableType<String>，以免出现循环依赖。
    private val typeCache: MutableMap<KType, ResolvableType<*>> = mutableMapOf()
) : ResolvableTypeBuilder<T> {
    private var nullable: Boolean = false
    private val rawClass: KClass<T & Any> = when {
        initialRawClass != null -> initialRawClass
        rawType != null -> rawType.classifier.toRawClass()
        else -> error("Raw class not found. ")
    }

    private fun KClassifier?.toRawClass(): KClass<T & Any> {
        require(this is KClass<*>) { "Classifier $this is not a class. Make sure the type is not a type alias. " }
        return this as KClass<T & Any>
    }

    private var outerType: ResolvableType<*>? = null

    private val typeParameters = rawClass.typeParameters
    private val typeParametersByName = typeParameters.associateBy { it.name }

    private var typeArgumentIndex = 0
    private val typeArgumentProjections = mutableMapOf<String, KTypeProjection>()

    private val annotations = mutableListOf<Annotation>()

    private fun createResolvableTypeBuilderWithSharedCache(rawType: KType): ResolvableTypeBuilder<*> {
        return ResolvableTypeBuilderImpl<Any>(typeResolver, rawType, initialRawClass = null, typeCache)
    }

    internal fun addTypeCache(type: ResolvableType<*>) {
        typeCache[type.rawType] = type
    }

    internal fun getCacheTypeOrResolve(rawType: KType): ResolvableTypeImpl<*> {
        return typeResolver?.getTypeCache(rawType) as ResolvableTypeImpl<*>?
            ?: typeCache[rawType] as ResolvableTypeImpl<*>?
            ?: createResolvableTypeBuilderWithSharedCache(rawType).build() as ResolvableTypeImpl<*>
    }

    override fun nullable(nullable: Boolean): ResolvableTypeBuilder<T> = apply { this.nullable = nullable }

    override fun typeArgument(name: String, type: ResolvableType<*>, variance: KVariance): ResolvableTypeBuilder<T> {
        require(name in typeParametersByName) { "Type parameter $name not found for $rawClass. " }
        typeArgumentProjections[name] = KTypeProjection(variance, type.rawType)
        typeCache[type.rawType] = type
        return this
    }

    override fun typeArgument(index: Int, type: ResolvableType<*>, variance: KVariance): ResolvableTypeBuilder<T> {
        require(index in typeParameters.indices) { "Type argument index $index out of bounds for $rawClass. " }
        val name = typeParameters[index].name
        return typeArgument(name, type, variance)
    }

    override fun typeArgument(type: ResolvableType<*>, variance: KVariance): ResolvableTypeBuilder<T> {
        typeArgument(typeArgumentIndex, type, variance)
        typeArgumentIndex++
        return this
    }

    override fun typeArgument(name: String, typeProjection: KTypeProjection): ResolvableTypeBuilder<T> {
        require(name in typeParametersByName) { "Type parameter $name not found for $rawClass. " }
        typeArgumentProjections[name] = typeProjection
        return this
    }

    override fun typeArgument(index: Int, typeProjection: KTypeProjection): ResolvableTypeBuilder<T> {
        require(index in typeParameters.indices) { "Type argument index $index out of bounds for $rawClass. " }
        val name = typeParameters[index].name
        return typeArgument(name, typeProjection)
    }

    override fun typeArgument(typeProjection: KTypeProjection): ResolvableTypeBuilder<T> {
        typeArgument(typeArgumentIndex, typeProjection)
        typeArgumentIndex++
        return this
    }

    override fun annotation(annotation: Annotation): ResolvableTypeBuilder<T> {
        annotations.add(annotation)
        return this
    }

    override fun outerType(type: ResolvableType<*>): ResolvableTypeBuilder<T> {
        val outerJavaClass = rawClass.java.enclosingClass
        requireNotNull(outerJavaClass) { "Can not set outer type $type for non-inner class $rawClass. " }
        require(type.rawClass.java == outerJavaClass) { "Outer type $type does not match enclosing class $outerJavaClass. " }

        outerType = type
        return this
    }

    private val Class<*>.isNotInnerClass: Boolean get() = isInterface || isEnum || isAnnotation

    private fun checkAndTrySetOuterType() {
        if (outerType != null) {
            return
        }

        val directOuterJavaClass = rawClass.java.enclosingClass ?: return
        var outerJavaClass: Class<*>? = directOuterJavaClass

        // 收集外部类型的类型参数。例如，Outer<T1, T2>.Inner<U1, U2>.Current，得到的列表为 [Outer<T1, T2>, Inner<U1, U2>]
        val outerTypeParameters = mutableListOf<TypeVariable<*>>()
        if (!rawClass.java.isNotInnerClass) {
            while (outerJavaClass != null) {
                if (outerJavaClass.isNotInnerClass) {
                    break
                }
                outerTypeParameters.addAll(outerJavaClass.typeParameters.toList())
                outerJavaClass = outerJavaClass.enclosingClass
            }
        }

        val rawOuterTypeParameters = if (outerTypeParameters.isNotEmpty()) {
            // 如果已经设置了 rawType，使用它填充这些类型。
            val rawType = rawType
            requireNotNull(rawType) {
                "Outer type not set for inner class $rawClass, and it can not be automatically filled " +
                        "caused by unknown type outer type arguments: ${outerTypeParameters.joinToString(", ") { it.name }}. "
            }

            val rawTypeArguments = rawType.arguments
            check(rawTypeArguments.size == outerTypeParameters.size + typeParameters.size) {
                "Outer type parameters size mismatch: ${rawTypeArguments.size} != ${outerTypeParameters.size + typeParameters.size}. "
            }

            rawTypeArguments.subList(rawTypeArguments.size - outerTypeParameters.size, rawTypeArguments.size)
        } else {
            directOuterJavaClass.kotlin.typeParameters.map {
                val upperBound = it.upperBounds.firstOrNull() ?: typeOf<Any?>()
                KTypeProjection.invariant(upperBound)
            }
        }

        val directRawOuterClass = directOuterJavaClass.kotlin as KClass<Any>
        val rawOuterType = directRawOuterClass.createType(rawOuterTypeParameters, false, emptyList())
        outerType = typeCache[rawOuterType]
            ?: ResolvableTypeBuilderImpl(typeResolver, rawOuterType, directRawOuterClass, typeCache).build()
    }

    override fun build(): ResolvableType<T> {
        checkAndTrySetOuterType()

        var rawType = rawType
        if (rawType == null) {
            val outerType = outerType
            val outerTypeArguments = outerType?.rawType?.arguments ?: emptyList()

            val arguments = outerTypeArguments + typeParameters.map {
                var result = typeArgumentProjections[it.name]
                if (result == null) {
                    val upperBound = it.upperBounds.firstOrNull() ?: typeOf<Any?>()
                    result = KTypeProjection.invariant(upperBound)
                }
                result
            }
            rawType = rawClass.createType(arguments, nullable, annotations)
        }

        return ResolvableTypeImpl(rawType, rawClass, outerType, this).apply {
            typeResolver?.addTypeCache(this)
        }
    }
}