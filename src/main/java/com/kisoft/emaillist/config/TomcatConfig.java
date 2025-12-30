package com.kisoft.emaillist.config;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat Configuration for handling large HTTP headers.
 * This configuration increases the maximum allowed HTTP header size to handle
 * large template payloads that include embedded images as base64 data.
 * The default Tomcat header size (8KB) is often insufficient when templates
 * contain large embedded images, causing "Request header is too large" errors.
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-28
 */
@Configuration
public class TomcatConfig {

    private static final int MAX_HEADER_SIZE_BYTES = 1024 * 1024; // 1 MB

    /**
     * Customizes the embedded Tomcat server to allow larger HTTP headers.
     * Sets max header size to 1MB to accommodate large template payloads.
     *
     * @return WebServerFactoryCustomizer for Tomcat configuration
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?> protocol) {
                protocol.setMaxHttpHeaderSize(MAX_HEADER_SIZE_BYTES);
            }
        });
    }
}
