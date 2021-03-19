import com.squareup.wire.Message
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

typealias WireUnaryMethod<Request, Response> = suspend (req: Request) -> Response
typealias WireOutboundMethod<Request, Response> = suspend (req: Request, resp: SendChannel<Response>) -> Unit
typealias WireInboundMethod<Request, Response> = suspend (req: ReceiveChannel<Request>) -> Response
typealias WireInboundOutboundMethod<Request, Response> = suspend (req: ReceiveChannel<Request>, resp: SendChannel<Response>) -> Unit

//typealias WireOutboundMethod<Request, Response> = KSuspendFunction1<ReceiveChannel<Request>, Response>
//typealias WireInboundOutboundMethod<Request, Response> = KSuspendFunction2<ReceiveChannel<Request>, SendChannel<Response>, Unit>
//typealias WireInboundMethod<Request, Response> = KSuspendFunction2<Request, SendChannel<Response>, Unit>

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


public class OutboundAdapter<Request, Response>(
    private val method: WireOutboundMethod<Request, Response>
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

public class InboundAdapter<Request, Response>(
    private val method: WireInboundMethod<Request, Response>
) : ServerCalls.ClientStreamingMethod<Request, Response> {
    override fun invoke(responseObserver: StreamObserver<Response>?): StreamObserver<Request> {

        val observer = responseObserver!!
        val requestChannel = Channel<Request>()

        GlobalScope.launch {
            try {
                observer.onNext(method.invoke(requestChannel))
                observer.onCompleted()
            } catch (e: Exception) {
                observer.onError(e)
            }
        }

        return object : StreamObserver<Request> {
            override fun onNext(value: Request) {
                requestChannel.offer(value)
            }

            override fun onError(t: Throwable?) {
                requestChannel.close(t)
            }

            override fun onCompleted() {
                requestChannel.close()
            }
        }
    }

}

public class InboundOutboundAdapter<Request, Response>(
    private val method: WireInboundOutboundMethod<Request, Response>
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
                requestChannel.offer(value)
            }

            override fun onError(t: Throwable?) {
                requestChannel.close(t)
            }

            override fun onCompleted() {
                requestChannel.close()
            }
        }
    }
}