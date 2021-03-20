package io.hadrien.wire

import io.grpc.Status
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

typealias WireUnaryMethod<Request, Response> = suspend (req: Request) -> Response
typealias WireServerStreamMethod<Request, Response> = suspend (req: Request, resp: SendChannel<Response>) -> Unit
typealias WireClientStreamMethod<Request, Response> = suspend (req: ReceiveChannel<Request>) -> Response
typealias WireBiStreamMethod<Request, Response> = suspend (req: ReceiveChannel<Request>, resp: SendChannel<Response>) -> Unit

public class UnaryAdapter<Request, Response>(
    private val method: WireUnaryMethod<Request, Response>,
) : ServerCalls.UnaryMethod<Request, Response> {
    override fun invoke(request: Request, responseObserver: StreamObserver<Response>?) {
        val observer = responseObserver!!
        GlobalScope.launch {
            try {
                observer.onNext(method.invoke(request))
                observer.onCompleted()
            } catch (e: Exception) {
                observer.onError(Status.fromThrowable(e).asException())
            }
        }
    }
}

public class ServerStreamAdapter<Request, Response>(
    private val method: WireServerStreamMethod<Request, Response>,
) : ServerCalls.ServerStreamingMethod<Request, Response> {
    override fun invoke(request: Request, responseObserver: StreamObserver<Response>?) {
        val responseChannel = Channel<Response>()
        val observer = responseObserver!!
        GlobalScope.launch {
            try {
                launch {
                    method.invoke(request, responseChannel)
                }
                for (value in responseChannel) {
                    observer.onNext(value)
                }
                observer.onCompleted()
            } catch (ex: Exception) {
                observer.onError(Status.fromThrowable(ex).asException())
            }
        }
    }
}

public class ClientStreamAdapter<Request, Response>(
    private val method: WireClientStreamMethod<Request, Response>,
) : ServerCalls.ClientStreamingMethod<Request, Response> {
    override fun invoke(responseObserver: StreamObserver<Response>?): StreamObserver<Request> {
        val requestChannel = Channel<Request>()
        val observer = responseObserver!!
        GlobalScope.launch {
            try {
                val response = method.invoke(requestChannel)
                observer.onNext(response)
                observer.onCompleted()
            } catch (e: Exception) {
                observer.onError(e)
            }
        }

        // TODO: Link the coroutines so cancellation and clean up is handled.
        return object : StreamObserver<Request> {
            override fun onNext(value: Request) {
                runBlocking {
                    requestChannel.send(value)
                }
            }

            override fun onError(t: Throwable?) {
                runBlocking {
                    requestChannel.close(t)
                }
            }

            override fun onCompleted() {
                runBlocking {
                    requestChannel.close()
                }
            }
        }
    }

}

public class BiDiStreamAdapter<Request, Response>(
    private val method: WireBiStreamMethod<Request, Response>,
) : ServerCalls.BidiStreamingMethod<Request, Response> {
    override fun invoke(responseObserver: StreamObserver<Response>?): StreamObserver<Request> {
        val requestChannel = Channel<Request>()
        val responseChannel = Channel<Response>()
        val observer = responseObserver!!
        GlobalScope.launch {
            try {
                launch {
                    method.invoke(requestChannel, responseChannel)
                }
                for (value in responseChannel) {
                    observer.onNext(value)
                }
                observer.onCompleted()
            } catch (e: Exception) {
                observer.onError(Status.fromThrowable(e).asException())
            }
        }

        // TODO: Link the coroutines so cancellation and clean up is handled.
        return object : StreamObserver<Request> {
            override fun onNext(value: Request) {
                runBlocking {
                    requestChannel.send(value)
                }
            }

            override fun onError(t: Throwable?) {
                runBlocking {
                    requestChannel.close(t)
                }
            }

            override fun onCompleted() {
                runBlocking {
                    requestChannel.close()
                }
            }
        }
    }
}