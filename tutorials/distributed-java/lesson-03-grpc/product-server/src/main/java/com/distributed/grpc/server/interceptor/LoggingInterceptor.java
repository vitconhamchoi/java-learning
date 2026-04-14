package com.distributed.grpc.server.interceptor;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC Logging Interceptor - log mọi gRPC calls với timing.
 *
 * Wrap cả request và response để capture:
 * - Method name
 * - Start time
 * - Duration
 * - Status code
 * - Request count (cho streaming)
 *
 * ForwardingServerCall: delegate tất cả calls đến original,
 * chỉ override method nào cần log thêm.
 */
@Component
public class LoggingInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        long startTime = System.currentTimeMillis();

        log.info("→ gRPC call started: method={}", methodName);

        // Wrap ServerCall để intercept response sending
        ServerCall<ReqT, RespT> loggingCall =
                new ForwardingServerCall.SimpleForwardingServerCall<>(call) {

                    @Override
                    public void sendMessage(RespT message) {
                        log.debug("  ↑ Sending response for method={}", methodName);
                        super.sendMessage(message);
                    }

                    @Override
                    public void close(Status status, Metadata trailers) {
                        long duration = System.currentTimeMillis() - startTime;
                        if (status.isOk()) {
                            log.info("✓ gRPC call completed: method={}, duration={}ms, status={}",
                                    methodName, duration, status.getCode());
                        } else {
                            log.warn("✗ gRPC call failed: method={}, duration={}ms, status={}, desc={}",
                                    methodName, duration, status.getCode(),
                                    status.getDescription());
                        }
                        super.close(status, trailers);
                    }
                };

        // Wrap Listener để intercept request events
        ServerCall.Listener<ReqT> listener = next.startCall(loggingCall, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {

            private int messageCount = 0;

            @Override
            public void onMessage(ReqT message) {
                messageCount++;
                log.debug("  ↓ Received message #{} for method={}", messageCount, methodName);
                super.onMessage(message);
            }

            @Override
            public void onComplete() {
                log.debug("  Client stream completed for method={}, total messages={}",
                        methodName, messageCount);
                super.onComplete();
            }

            @Override
            public void onCancel() {
                long duration = System.currentTimeMillis() - startTime;
                log.warn("  Client cancelled call: method={}, duration={}ms", methodName, duration);
                super.onCancel();
            }
        };
    }
}
