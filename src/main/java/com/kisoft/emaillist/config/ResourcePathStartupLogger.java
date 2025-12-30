package com.kisoft.emaillist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resource Path Startup Logger - Logs resolved paths of configuration and resource files.
 * This component logs the full paths of key configuration files at application startup,
 * helping developers verify which files are being used. In development, resources resolve
 * to real files on disk. In a packaged JAR, they resolve to {@code jar:} URLs.
 * Logged Resources:
 * - Working directory
 * - {@code application.properties} location
 * - {@code logback-spring.xml} location
 * - Email list file (external and classpath)
 * - Thymeleaf templates
 * - Static resources (JS, CSS)
 * Configuration:
 * - {@code app.logging.resource-paths.enabled} - Enable/disable logging (default: true)
 * - {@code email.list.file} - Email list filename (default: email-list.txt)
 * Security note: This logs file locations only, not secrets or configuration values.
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see org.springframework.boot.context.event.ApplicationReadyEvent
 * @see org.springframework.core.io.ResourceLoader
 */
@Component
public class ResourcePathStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(ResourcePathStartupLogger.class);

    private final ResourceLoader resourceLoader;
    private final ResourcePatternResolver resourcePatternResolver;

    @Value("${app.logging.resource-paths.enabled:true}")
    private boolean enabled;

    @Value("${email.list.file:email-list.txt}")
    private String emailListFile;

    @Value("${app.logging.resource-paths.template-pattern:classpath:/templates/**/*.html}")
    private String templatePattern;

    public ResourcePathStartupLogger(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logResourcePaths() {
        if (!enabled) {
            log.debug("Resource path startup logging is disabled (app.logging.resource-paths.enabled=false)");
            return;
        }

        Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

        log.info("""
                Resolved configuration/resource locations:
                  workingDir={}
                  applicationProperties={}
                  logbackConfig={}
                  emailList.external={} (exists={})
                  emailList.classpath={}
                  template.index={}
                  templates.all={}
                  static.js.app={}
                  static.css.app={}
                """,
            workingDir,
            resolveApplicationPropertiesLocation(),
            describeResource(resourceLoader.getResource("classpath:logback-spring.xml")),
            externalEmailListPath(workingDir),
            externalEmailListExists(workingDir),
            describeResource(resourceLoader.getResource("classpath:" + emailListFile)),
            describeResource(resourceLoader.getResource("classpath:/templates/index.html")),
            describeResources(templatePattern),
            describeResource(resourceLoader.getResource("classpath:/static/js/app.js")),
            describeResource(resourceLoader.getResource("classpath:/static/css/app.css"))
        );
    }

    private String resolveApplicationPropertiesLocation() {
        // Spring Boot loads config from many places; we can log the most common ones.
        // 1) External file in working directory
        Path external = Paths.get(System.getProperty("user.dir"), "application.properties")
            .toAbsolutePath()
            .normalize();
        if (java.nio.file.Files.exists(external)) {
            return external.toString();
        }
        // 2) Classpath resource
        return describeResource(resourceLoader.getResource("classpath:application.properties"));
    }

    private String externalEmailListPath(Path workingDir) {
        return getExternalEmailListPath(workingDir).toString();
    }

    private boolean externalEmailListExists(Path workingDir) {
        return java.nio.file.Files.exists(getExternalEmailListPath(workingDir));
    }

    private Path getExternalEmailListPath(Path workingDir) {
        // Keep in sync with current application behavior: external file is located in working dir
        // and is named email-list.txt. (EmailListService currently hardcodes this name.)
        return workingDir.resolve("email-list.txt").normalize();
    }

    private String describeResources(String pattern) {
        try {
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            if (resources == null || resources.length == 0) {
                return "<none>";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < resources.length; i++) {
                if (i > 0) sb.append("; ");
                sb.append(describeResource(resources[i]));
            }
            return sb.toString();
        } catch (IOException e) {
            return "<unresolvable: " + e.getClass().getSimpleName() + ">";
        }
    }

    private static String describeResource(Resource resource) {
        if (resource == null) {
            return "<null>";
        }
        if (!resource.exists()) {
            return "<missing>";
        }

        try {
            // Prefer absolute file path where available (dev/test).
            return resource.getFile().toPath().toAbsolutePath().normalize().toString();
        } catch (IOException ignored) {
            // Packaged JARs typically can't resolve to a java.io.File.
        }

        try {
            return resource.getURL().toString();
        } catch (IOException e) {
            return "<unresolvable:" + e.getClass().getSimpleName() + ">";
        }
    }
}
