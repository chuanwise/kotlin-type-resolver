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

import kotlin.reflect.KTypeParameter

interface ResolvableTypeArgumentOwner {
    val typeParameters: List<KTypeParameter>
    val typeParametersByName: Map<String, KTypeParameter>

    val typeArguments: List<ResolvableTypeArgument>
    val typeArgumentsByName: Map<String, ResolvableTypeArgument>

    val isResolved: Boolean

    fun getTypeArgument(name: String): ResolvableTypeArgument? {
        return typeArgumentsByName[name]
    }
    fun getTypeArgumentOrFail(name: String): ResolvableTypeArgument {
        return getTypeArgument(name) ?: throw IllegalArgumentException("No type argument named $name")
    }
}