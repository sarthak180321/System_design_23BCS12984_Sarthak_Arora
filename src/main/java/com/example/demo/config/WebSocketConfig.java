package com.example.demo.config;

import com.example.demo.handler.StreamWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(streamWebSocketHandler(), "/stream/{userId}/{streamId}")
                .setAllowedOrigins("*");
    }

    @Bean
    public StreamWebSocketHandler streamWebSocketHandler() {
        return new StreamWebSocketHandler();
    }

    // THIS is what actually sets the buffer size in Tomcat
    // application.properties alone does not work for WebSocket
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

        // 10MB buffer — MediaRecorder chunks are typically 50-200KB
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);

        // Keep connection alive — no timeout during streaming
        container.setMaxSessionIdleTimeout(0L);

        return container;
    }
}