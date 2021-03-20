/**
 * Copyright 2021 Hadrien Kohl
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hadrien.wire

import com.squareup.wire.ProtoAdapter
import io.grpc.MethodDescriptor
import java.io.InputStream

class WireMarshaller<Type>(
    private val adapter: ProtoAdapter<Type>
) : MethodDescriptor.Marshaller<Type> {
    override fun stream(value: Type): InputStream = adapter.encode(value).inputStream()
    override fun parse(stream: InputStream): Type = adapter.decode(stream)
}