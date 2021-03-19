// Code generated by Wire protocol buffer compiler, do not edit.
// Source: io.hadrien.wire.TestGrpcService in test.proto
package io.hadrien.wire

import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcStreamingCall
import com.squareup.wire.Service

public interface TestGrpcServiceClient : Service {
  public fun Unary(): GrpcCall<Input, Output>

  public fun Outbound(): GrpcStreamingCall<Input, Output>

  public fun Inbound(): GrpcStreamingCall<Input, Output>

  public fun InboundOutbound(): GrpcStreamingCall<Input, Output>
}