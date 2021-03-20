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

import com.squareup.wire.GrpcClient
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

internal class ReflectionBindableServiceTest {

    lateinit var client: TestGrpcServiceClient
    lateinit var server: Server

    @BeforeEach
    internal fun setUp() {
        // Start a server for testing.
        val bindableService = ReflectionBindableService(TestServiceImpl())
        server = ServerBuilder.forPort(8080)
            .addService(bindableService)
            .build()
            .start()

        // Create a test client.
        val grpcClient = GrpcClient.Builder()
            .client(
                OkHttpClient.Builder()
                    .callTimeout(1, TimeUnit.HOURS)
                    .connectTimeout(1, TimeUnit.HOURS)
                    .readTimeout(1, TimeUnit.HOURS)
                    .writeTimeout(1, TimeUnit.HOURS)
                    .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build()
            )
            .baseUrl("http://localhost:8080")
            .build()
        client = grpcClient.create(TestGrpcServiceClient::class)
    }

    @AfterEach
    internal fun tearDown() {
        server.shutdownNow()
    }

    @Test
    internal fun testUnary() {
        val output = client.Unary().executeBlocking(Input("testUnary"))
        assert(output.content == "testUnary")
    }

    @Test
    internal fun testClientStream() {
        val (messageSink, messageSource) = client.Inbound().executeBlocking()
        messageSink.write(Input("message1"))
        messageSink.write(Input("message2"))
        messageSink.write(Input("message3"))
        messageSink.close()
        val output = messageSource.read()
        messageSource.close()
        assert(output!!.content == "message1message2message3")
    }

    @Test
    internal fun testServerStream() {
        val (messageSink, messageSource) = client.Outbound().executeBlocking()
        messageSink.write(Input("message"))
        messageSink.close()
        val responses = generateSequence { messageSource.read() }
            .map { it.content }
            .toList()
        assert(
            responses == listOf(
                "message",
                "message",
            )
        )
    }

    @Test
    internal fun testBiStream() {
        val (messageSink, messageSource) = client.InboundOutbound().executeBlocking()
        messageSink.write(Input("message1"))
        messageSink.write(Input("message2"))
        messageSink.close()
        val responses = generateSequence { messageSource.read() }
            .map { it.content }
            .toList()


        assert(
            responses == listOf(
                "message1",
                "message1",
                "message1message2",
                "message1message2",
            )
        )
    }
}

class TestServiceImpl : TestGrpcServiceServer {

    override suspend fun Unary(request: Input): Output {
        println("Unary")
        return Output(request.content)
    }

    override suspend fun Outbound(request: Input, response: SendChannel<Output>) {
        println("Outbound")
        for (i in 1..2) {
            println("sending response #$i")
            response.send(Output(request.content))
        }
        response.close()
    }

    override suspend fun Inbound(request: ReceiveChannel<Input>): Output {
        println("Inbound")
        var output = ""
        for (input in request) {
            output += input.content
        }
        return Output(output)
    }

    override suspend fun InboundOutbound(request: ReceiveChannel<Input>, response: SendChannel<Output>) {
        println("InboundOutbound")
        var output = ""
        for (input in request) {
            output += input.content
            for (i in 1..2) {
                response.send(Output(output))
            }
        }
        response.close()
    }

}
