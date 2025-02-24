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

@file:JvmName("ResolvableFunctionFactory")

package cn.chuanwise.typeresolver

import kotlin.reflect.KFunction

@JvmOverloads
fun <T> createResolvableFunction(
    rawFunction: KFunction<T>,
    typeResolver: TypeResolver = createTypeResolver()
) : ResolvableFunction<T> {
    return ResolvableFunctionImpl(null, rawFunction, typeResolver)
}

@JvmOverloads
fun <T> KFunction<T>.toResolvableFunction(
    typeResolver: TypeResolver = createTypeResolver()
) : ResolvableFunction<T> {
    return createResolvableFunction(this, typeResolver)
}