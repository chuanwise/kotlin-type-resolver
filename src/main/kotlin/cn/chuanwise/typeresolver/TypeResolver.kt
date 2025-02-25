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

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType

interface TypeResolver {
    fun resolve(type: KType): ResolvableType<*>
}

internal class TypeResolverImpl : TypeResolver {
    private val typeCache = ConcurrentHashMap<KType, ResolvableType<*>>()

    internal fun getTypeCache(type: KType): ResolvableType<*>? {
        return typeCache[type]
    }

    internal fun addTypeCache(type: ResolvableType<*>) {
        typeCache[type.rawType] = type
    }

    override fun resolve(type: KType): ResolvableType<*> {
        typeCache[type]?.let { return it }

        return createResolvableTypeBuilder(type, this)
            .build()
            .apply {
                if (isResolved) {
                    typeCache[type] = this
                }
            }
    }
}
