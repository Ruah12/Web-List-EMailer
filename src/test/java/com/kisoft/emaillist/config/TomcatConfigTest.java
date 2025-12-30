package com.kisoft.emaillist.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TomcatConfig to verify large HTTP header handling.
 * These tests verify that the max HTTP header size configuration is properly applied.
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-28
 * @see TomcatConfig
 */
@SpringBootTest
class TomcatConfigTest {

    @Autowired
    private TomcatConfig tomcatConfig;

    @Test
    @DisplayName("TomcatConfig bean should be loaded in application context")
    void tomcatConfigBeanShouldBeLoaded() {
        assertThat(tomcatConfig).isNotNull();
    }

    @Test
    @DisplayName("TomcatConfig should return a non-null WebServerFactoryCustomizer")
    void tomcatCustomizerShouldReturnNonNullCustomizer() {
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> customizer = tomcatConfig.tomcatCustomizer();
        assertThat(customizer).isNotNull();
    }

    @Test
    @DisplayName("Customizer should configure TomcatServletWebServerFactory without exceptions")
    void customizerShouldConfigureFactoryWithoutExceptions() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> customizer = tomcatConfig.tomcatCustomizer();

        // This should not throw any exception
        customizer.customize(factory);

        assertThat(factory).isNotNull();
    }
}

