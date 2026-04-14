package com.distributed.grpc.server.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC Server Interceptor để xác thực Authorization header.
 *
 * Tương tự Servlet Filter trong REST nhưng dành cho gRPC.
 * Interceptor chạy trước khi request đến service handler.
 *
 * Metadata trong gRPC tương đương HTTP Headers.
 */
@Component
public class AuthInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    // Key để đọc Authorization header từ gRPC Metadata
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    // Context key để truyền auth info vào service handler (nếu cần)
    public static final Context.Key<String> CLIENT_ID_KEY = Context.key("clientId");

    // Token hợp lệ (trong thực tế verify với JWT library)
    private static final String VALID_TOKEN = "Bearer valid-token-123";

    /**
     * Intercept mọi gRPC call trước khi đến service handler.
     *
     * @param call    Thông tin về gRPC call (method, attributes)
     * @param headers Metadata headers từ client (tương tự HTTP headers)
     * @param next    Handler tiếp theo trong chain
     * @return ServerCall.Listener để xử lý events (onMessage, onComplete, etc.)
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        String authHeader = headers.get(AUTHORIZATION_KEY);

        log.debug("Auth interceptor: method={}, hasAuth={}", methodName, authHeader != null);

        // Kiểm tra Authorization header
        if (authHeader == null || authHeader.isBlank()) {
            log.warn("Missing Authorization header for method: {}", methodName);
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Authorization header is required"),
                    new Metadata()
            );
            return new ServerCall.Listener<>() {}; // Empty listener (call already closed)
        }

        // Validate token (simplified - in production use JWT validation)
        if (!VALID_TOKEN.equals(authHeader)) {
            log.warn("Invalid token for method: {}, token: {}...",
                    methodName,
                    authHeader.length() > 20 ? authHeader.substring(0, 20) : authHeader);
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Invalid or expired token"),
                    new Metadata()
            );
            return new ServerCall.Listener<>() {};
        }

        // Token hợp lệ: extract client ID và đặt vào Context
        String clientId = extractClientId(authHeader);
        Context context = Context.current().withValue(CLIENT_ID_KEY, clientId);

        log.debug("Auth successful: method={}, clientId={}", methodName, clientId);

        // Tiếp tục xử lý với context đã có client info
        return Contexts.interceptCall(context, call, headers, next);
    }

    /** Extract client ID từ token (trong thực tế decode JWT) */
    private String extractClientId(String authHeader) {
        // Simplified: return "client-123" for valid token
        return "client-" + authHeader.hashCode();
    }
}
