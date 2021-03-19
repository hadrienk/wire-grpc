import com.squareup.wire.ProtoAdapter
import io.grpc.MethodDescriptor
import java.io.InputStream

class WireMarshaller<Type>(
    private val adapter: ProtoAdapter<Type>
) : MethodDescriptor.Marshaller<Type> {
    override fun stream(value: Type): InputStream = adapter.encode(value).inputStream()
    override fun parse(stream: InputStream): Type = adapter.decode(stream)
}