Wire GRPC
===

Create [grpc](https://www.grpc.io/docs/languages/java/basics/#starting-the-server) BindableService using your [wire](https://github.com/square/wire/) generated interfaces. Simply wrap your service implementation in a ReflectionBindableService class.

```
class ServiceImpl : SomeServiceServer {
  override suspend fun Unary(request: Input): Output {
    // [...]
  }

  override suspend fun ServerStream(request: Input, response: SendChannel<Output>) {
    // [...]
  }

  override suspend fun ClientStream(request: ReceiveChannel<Input>): Output {
    // [...]
  }

  override suspend fun BiDiStream(request: ReceiveChannel<Input>, response: SendChannel<Output>) {
    // [...]
  }

}

fun main() {
    val bindableService = ReflectionBindableService(ServiceImpl())
    server = ServerBuilder.forPort(8080)
        .addService(bindableService)
        .build()
        .start()
}
```