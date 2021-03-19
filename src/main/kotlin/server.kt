import io.grpc.ServerBuilder
import io.mikromaskin.rpc.firmware.Input
import io.mikromaskin.rpc.firmware.Output
import io.mikromaskin.rpc.firmware.UpdateServiceServer
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay

fun main() {

    val dynamicService = ReflectionBindableService(UpdateServiceImpl())
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