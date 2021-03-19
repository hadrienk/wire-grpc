import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import io.grpc.BindableService
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.Marshaller
import io.grpc.ServerBuilder
import io.grpc.ServerServiceDefinition
import io.grpc.stub.ServerCalls
import io.mikromaskin.rpc.firmware.Input
import io.mikromaskin.rpc.firmware.Output
import io.mikromaskin.rpc.firmware.UpdateServiceServer
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import java.io.InputStream
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*

fun main() {

    val dynamicService = DynamicService()
    dynamicService.addService(UpdateServiceImpl());

    val server = ServerBuilder.forPort(8080)
        .addService(dynamicService)
        .build()

    server.start()
    server.awaitTermination()
}

/**
 * Server implementation.
 */
class UpdateServiceImpl : UpdateServiceServer {
    override suspend fun Unary(request: Input): Output {
        println("Unary")
        return Output(request.content)
    }

    override suspend fun Outbound(request: Input, response: SendChannel<Output>) {
        println("Outbound")
        for (i in 1..4) {
            println("sending response #$i")
            response.send(Output(request.content))
            delay(250)
        }
        response.close()
    }

    override suspend fun Inbound(request: ReceiveChannel<Input>): Output {
        println("Inbound")
        var output = ""
        for (input in request) {
            output += input.content
            delay(250)
        }
        return Output(output)
    }

    override suspend fun InboundOutbound(request: ReceiveChannel<Input>, response: SendChannel<Output>) {
        println("InboundOutbound")
        var output = ""
        for (input in request) {
            output += input.content
            for (i in 1..4) {
                response.send(Output(output))
                delay(250)
            }
        }
        response.close()
    }
}

class WireMarshaller<Type>(
    private val adapter: ProtoAdapter<Type>
) : Marshaller<Type> {
    override fun stream(value: Type): InputStream = adapter.encode(value).inputStream()
    override fun parse(stream: InputStream): Type = adapter.decode(stream)
}

/**
 * Links the server and Grpc.
 */
public class DynamicService : BindableService {


    private var definition: ServerServiceDefinition? = null

    val server = UpdateServiceImpl()

    public fun <T : Service> addService(service: T, kClass: KClass<out T>) {
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

        // TODO: Only one service name makes sense.

        for ((serviceName, functions) in serviceMap) {
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

                when (methodType) {
                    MethodDescriptor.MethodType.UNARY -> {
                        val unary: WireUnaryMethod<Message<*, *>, Message<*, *>> =
                            { input: Any -> method.callSuspend(service, input) as Message<*, *> }
                        val unaryAdapter: ServerCalls.UnaryMethod<Message<*, *>, Message<*, *>> = UnaryAdapter(unary)
                        val handler = ServerCalls.asyncUnaryCall(unaryAdapter)
                        builder.addMethod(
                            methodDescriptor,
                            handler
                        )
                    }
                    MethodDescriptor.MethodType.CLIENT_STREAMING -> {
                        val clientStream: WireInboundMethod<Message<*, *>, Message<*, *>> =
                            { req: ReceiveChannel<*> -> method.callSuspend(service, req) as Message<*, *> }
                        val clientAdapter: ServerCalls.ClientStreamingMethod<Message<*, *>, Message<*, *>> =
                            InboundAdapter(clientStream)
                        val handler = ServerCalls.asyncClientStreamingCall(clientAdapter)
                        builder.addMethod(
                            methodDescriptor,
                            handler
                        )
                    }
                    MethodDescriptor.MethodType.SERVER_STREAMING -> {
                        var serverStream: WireOutboundMethod<Message<*, *>, Message<*, *>> =
                            { any, resp -> method.callSuspend(service, any, resp) }
                        val serverAdapter: ServerCalls.ServerStreamingMethod<Message<*, *>, Message<*, *>> =
                            OutboundAdapter(serverStream)
                        val handler = ServerCalls.asyncServerStreamingCall(serverAdapter)
                        builder.addMethod(
                            methodDescriptor,
                            handler
                        )

                    }
                    MethodDescriptor.MethodType.BIDI_STREAMING -> {
                        val biStream: WireInboundOutboundMethod<Message<*, *>, Message<*, *>> =
                            { req, resp -> method.callSuspend(service, req, resp) }
                        val biAdapter: ServerCalls.BidiStreamingMethod<Message<*, *>, Message<*, *>> =
                            InboundOutboundAdapter(biStream)
                        val handler = ServerCalls.asyncBidiStreamingCall(biAdapter)
                        builder.addMethod(
                            methodDescriptor,
                            handler
                        )
                    }
                    MethodDescriptor.MethodType.UNKNOWN -> {
                        throw IllegalStateException("unknow method type for ${method.name}")
                    }
                }
            }
            definition = builder.build();
        }
    }

    public fun addService(service: Service) {
        addService(service, service::class)
    }

    private fun loadAdapter(requestAdapter: String): ProtoAdapter<*> {
        val (companionName, fieldName) = requestAdapter.split("#")
        val companion = Class.forName(companionName).kotlin.companionObjectInstance
        val companionClass = Class.forName(companionName).kotlin.companionObject!!.members.find { it.name == fieldName }
        return companionClass!!.call(companion) as ProtoAdapter<*>
    }

    private fun guessType(method: KFunction<*>): MethodDescriptor.MethodType {
        return when (method.name) {
            "Unary" -> MethodDescriptor.MethodType.UNARY
            "Outbound" -> MethodDescriptor.MethodType.SERVER_STREAMING
            "Inbound" -> MethodDescriptor.MethodType.CLIENT_STREAMING
            "InboundOutbound" -> MethodDescriptor.MethodType.BIDI_STREAMING
            else -> MethodDescriptor.MethodType.UNKNOWN
        }
    }

//    fun manualBind(): ServerMethodDefinition {
//        // Should be annotated in the interface.
//        val serviceName = "mikromaskin.firmware.UpdateService"
//
//        val builder = ServerServiceDefinition.builder(serviceName)
//
//        val outboundMethod = MethodDescriptor.newBuilder<Input, Output>()
//            .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
//            .setFullMethodName(
//                MethodDescriptor.generateFullMethodName(
//                    "mikromaskin.firmware.UpdateService", "Outbound"
//                )
//            )
//            .setSampledToLocalTracing(true)
//            .setRequestMarshaller(WireMarshaller(Input.ADAPTER))
//            .setResponseMarshaller(WireMarshaller(Output.ADAPTER))
//            .build()
//        builder.addMethod(
//            outboundMethod,
//            ServerCalls.asyncServerStreamingCall(OutboundAdapter<Input, Output>(server::Outbound))
//        )
//
//        val inboundMethod = MethodDescriptor.newBuilder<Input, Output>()
//            .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
//            .setFullMethodName(
//                MethodDescriptor.generateFullMethodName(
//                    "mikromaskin.firmware.UpdateService", "Inbound"
//                )
//            )
//            .setSampledToLocalTracing(true)
//            .setRequestMarshaller(WireMarshaller(Input.ADAPTER))
//            .setResponseMarshaller(WireMarshaller(Output.ADAPTER))
//            .build()
//        builder.addMethod(
//            inboundMethod,
//            ServerCalls.asyncClientStreamingCall(InboundAdapter<Input, Output>(server::Inbound))
//        )
//
//        val inboundOutboundMethod = MethodDescriptor.newBuilder<Input, Output>()
//            .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
//            .setFullMethodName(
//                MethodDescriptor.generateFullMethodName(
//                    "mikromaskin.firmware.UpdateService", "InboundOutbound"
//                )
//            )
//            .setSampledToLocalTracing(true)
//            .setRequestMarshaller(WireMarshaller(Input.ADAPTER))
//            .setResponseMarshaller(WireMarshaller(Output.ADAPTER))
//            .build()
//
//        builder.addMethod(
//            inboundOutboundMethod,
//            ServerCalls.asyncBidiStreamingCall(InboundOutboundAdapter<Input, Output>(server::InboundOutbound))
//        )
//
//        val unaryMethod = MethodDescriptor.newBuilder<Input, Output>()
//            .setType(MethodDescriptor.MethodType.UNARY)
//            .setFullMethodName(
//                MethodDescriptor.generateFullMethodName(
//                    "mikromaskin.firmware.UpdateService", "Unary"
//                )
//            )
//            .setSampledToLocalTracing(true)
//            .setRequestMarshaller(WireMarshaller(Input.ADAPTER))
//            .setResponseMarshaller(WireMarshaller(Output.ADAPTER))
//            .build()
//
//        builder.addMethod(
//            unaryMethod,
//            ServerCalls.asyncUnaryCall(UnaryAdapter(server::Unary))
//        )
//
//        return builder.build()
//    }

    override fun bindService(): ServerServiceDefinition {
        return definition!!
    }
}