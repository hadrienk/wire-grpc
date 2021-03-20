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

import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import io.grpc.BindableService
import io.grpc.MethodDescriptor
import io.grpc.ServerCallHandler
import io.grpc.ServerServiceDefinition
import io.grpc.stub.ServerCalls
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*

/**
 * A bindable service that uses reflection.
 *
 * The io.hadrien.wire.ReflectionBindableService analyses a wire generated service
 * and exposes it as a GRPC ServerServiceDefinition
 */
class ReflectionBindableService<T : Service>(
    private val service: T,
    private val kClass: KClass<out T>,
) : BindableService {

    constructor(service: T) : this(service, service::class)

    private fun loadAdapter(requestAdapter: String): ProtoAdapter<*> {
        val (companionName, fieldName) = requestAdapter.split("#")
        val companion = Class.forName(companionName).kotlin.companionObjectInstance
        val companionClass = Class.forName(companionName).kotlin.companionObject!!.members.find { it.name == fieldName }
        return companionClass!!.call(companion) as ProtoAdapter<*>
    }

    private fun guessType(method: KFunction<*>): MethodDescriptor.MethodType {
        return when {
            isUnary(method) -> MethodDescriptor.MethodType.UNARY
            isServerStream(method) -> MethodDescriptor.MethodType.SERVER_STREAMING
            isClientStream(method) -> MethodDescriptor.MethodType.CLIENT_STREAMING
            isBiDiStream(method) -> MethodDescriptor.MethodType.BIDI_STREAMING
            else -> MethodDescriptor.MethodType.UNKNOWN
        }
    }

    private fun isUnary(method: KFunction<*>): Boolean {
        return method.parameters.size == 2
                && method.parameters[1].type.classifier != ReceiveChannel::class
    }

    private fun isClientStream(method: KFunction<*>): Boolean {
        return method.parameters.size == 2
                && method.parameters[1].type.classifier == ReceiveChannel::class
    }

    private fun isServerStream(method: KFunction<*>): Boolean {
        return method.parameters.size == 3
                && method.parameters[1].type.classifier != ReceiveChannel::class
                && method.parameters[2].type.classifier == SendChannel::class
    }

    private fun isBiDiStream(method: KFunction<*>): Boolean {
        return method.parameters.size == 3
                && method.parameters[1].type.classifier == ReceiveChannel::class
                && method.parameters[2].type.classifier == SendChannel::class
    }

    override fun bindService(): ServerServiceDefinition {
        // First superclass should be the service.
        // TODO: Maybe stop at the type that implements Service instead.
        val serviceInterface = kClass.superclasses[0]

        val annotatedMethods = serviceInterface.declaredFunctions
            .filter { it.hasAnnotation<WireRpc>() }

        // Map all services
        val serviceMap = annotatedMethods
            .groupBy {
                val annotation = it.annotations.find { annotation -> annotation is WireRpc } as WireRpc
                annotation.path.removePrefix("/").split("/").first()
            }

        if (serviceMap.size != 1) throw IllegalStateException("more than one service in interface")
        val (serviceName, functions) = serviceMap.entries.first()

        val functionsMap =
            functions.associateBy { it.annotations.find { annotation -> annotation is WireRpc } as WireRpc }
        val builder = ServerServiceDefinition.builder(serviceName)
        for ((annotation, method) in functionsMap) {
            // Find the adapters.
            val requestAdapter = loadAdapter(annotation.requestAdapter) as ProtoAdapter<Message<*, *>>
            val responseAdapter = loadAdapter(annotation.responseAdapter) as ProtoAdapter<Message<*, *>>
            val requestMarshaller = WireMarshaller(requestAdapter)
            val responseMarshaller = WireMarshaller(responseAdapter)
            val methodType = guessType(method)
            val methodDescriptor = MethodDescriptor.newBuilder<Message<*, *>, Message<*, *>>()
                .setFullMethodName(annotation.path.removePrefix("/"))
                .setType(methodType)
                .setRequestMarshaller(requestMarshaller)
                .setResponseMarshaller(responseMarshaller)
                .build()
            builder.addMethod(methodDescriptor, createHandler(methodType, method))
        }
        return builder.build()
    }

    private fun createHandler(
        methodType: MethodDescriptor.MethodType,
        method: KFunction<*>,
    ): ServerCallHandler<Message<*, *>, Message<*, *>> {
        return when (methodType) {
            MethodDescriptor.MethodType.UNARY -> ServerCalls.asyncUnaryCall(UnaryAdapter { input: Any ->
                method.callSuspend(service,
                    input) as Message<*, *>
            })
            MethodDescriptor.MethodType.CLIENT_STREAMING -> ServerCalls.asyncClientStreamingCall(ClientStreamAdapter { req: ReceiveChannel<*> ->
                method.callSuspend(service,
                    req) as Message<*, *>
            })
            MethodDescriptor.MethodType.SERVER_STREAMING -> ServerCalls.asyncServerStreamingCall(ServerStreamAdapter { any, resp ->
                method.callSuspend(service,
                    any,
                    resp)
            })
            MethodDescriptor.MethodType.BIDI_STREAMING -> ServerCalls.asyncBidiStreamingCall(BiDiStreamAdapter { req, resp ->
                method.callSuspend(service,
                    req,
                    resp)
            })
            MethodDescriptor.MethodType.UNKNOWN -> {
                throw IllegalStateException("unknown method type for ${method.name}")
            }
        }
    }
}