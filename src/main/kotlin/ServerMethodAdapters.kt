import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

typealias WireUnaryMethod<Request, Response> = suspend (req: Request) -> Response
typealias WireServerStreamMethod<Request, Response> = suspend (req: Request, resp: SendChannel<Response>) -> Unit
typealias WireClientStreamMethod<Request, Response> = suspend (req: ReceiveChannel<Request>) -> Response
typealias WireBiStreamMethod<Request, Response> = suspend (req: ReceiveChannel<Request>, resp: SendChannel<Response>) -> Unit

public class UnaryAdapter<Request, Response>(
    private val method: WireUnaryMethod<Request, Response>
) : ServerCalls.UnaryMethod<Request, Response> {
    override fun invoke(request: Request, responseObserver: StreamObserver<Response>?) {
        val observer = responseObserver!!
        try {
            GlobalScope.launch {
                observer.onNext(method.invoke(request))
                observer.onCompleted()
            }
        } catch (e: Exception) {
            observer.onError(e)
        }
    }
}


public class ServerStreamAdapter<Request, Response>(
    private val method: WireServerStreamMethod<Request, Response>
) : ServerCalls.ServerStreamingMethod<Request, Response> {
    override fun invoke(request: Request, response: StreamObserver<Response>?) {
        // TODO: Improve.
        val responseChannel = Channel<Response>()
        try {
            GlobalScope.launch {
                method.invoke(request!!, responseChannel)
            }
            GlobalScope.launch {
                for (value in responseChannel) {
                    response!!.onNext(value)
                }
                response!!.onCompleted()
            }
        } catch (e: Exception) {
            response!!.onError(e)
        }
    }
}

public class ClientStreamAdapter<Request, Response>(
    private val method: WireClientStreamMethod<Request, Response>
) : ServerCalls.ClientStreamingMethod<Request, Response> {
    override fun invoke(responseObserver: StreamObserver<Response>?): StreamObserver<Request> {

        val observer = responseObserver!!
        val requestChannel = Channel<Request>()

        GlobalScope.launch {
            try {
                val response = method.invoke(requestChannel)
                observer.onNext(response)
                observer.onCompleted()
            } catch (e: Exception) {
                observer.onError(e)
            }
        }

        return object : StreamObserver<Request> {
            override fun onNext(value: Request) {
                GlobalScope.launch {
                    requestChannel.send(value)
                }
            }

            override fun onError(t: Throwable?) {
                GlobalScope.launch {
                    requestChannel.close(t)
                }
            }

            override fun onCompleted() {
                GlobalScope.launch {
                    requestChannel.close()
                }
            }
        }
    }

}

public class BiDiStreamAdapter<Request, Response>(
    private val method: WireBiStreamMethod<Request, Response>
) : ServerCalls.BidiStreamingMethod<Request, Response> {
    override fun invoke(responseObserver: StreamObserver<Response>?): StreamObserver<Request> {
        val requestChannel = Channel<Request>()
        val responseChannel = Channel<Response>()
        try {
            GlobalScope.launch {
                method.invoke(requestChannel, responseChannel)
            }

            GlobalScope.launch {
                for (value in responseChannel) {
                    responseObserver!!.onNext(value)
                }
                responseObserver!!.onCompleted()
            }
        } catch (e: Exception) {
            responseObserver!!.onError(e)
        }

        return object : StreamObserver<Request> {
            override fun onNext(value: Request) {
                GlobalScope.launch {
                        requestChannel.send(value)
                }
            }

            override fun onError(t: Throwable?) {
                GlobalScope.launch {
                    requestChannel.close(t)
                }
            }

            override fun onCompleted() {
                GlobalScope.launch {
                    requestChannel.close()
                }
            }
        }
    }
}