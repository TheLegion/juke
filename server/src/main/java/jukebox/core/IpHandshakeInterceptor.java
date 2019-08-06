package jukebox.core;

import java.util.List;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

public class IpHandshakeInterceptor implements HandshakeInterceptor {

    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        List<String> value = request.getHeaders().get("X-Real-IP");
        if (value == null || value.isEmpty()) {
            throw new RuntimeException("Empty X-Real-IP");
        }
        attributes.put("ip", value.get(0));
        return true;
    }

    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
    }
}
