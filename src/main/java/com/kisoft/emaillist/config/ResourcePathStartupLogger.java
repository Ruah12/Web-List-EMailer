package com.kisoft.emaillist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Logs the resolved origins/paths of key configuration and resource files.
 *
 * <p>In development, many resources resolve to real files on disk. In a packaged
 * executable JAR they may resolve to {@code jar:} URLs instead. This component
 * logs the best available location without failing startup.</p>
 *
 * <p>Security note: this logs file locations only. It does not log any secrets
 * or configuration values.</p>
 */
@Component
public class ResourcePathStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(ResourcePathStartupLogger.class);

    private final ResourceLoader resourceLoader;

    @Value("${app.logging.resource-paths.enabled:true}")
    private boolean enabled;

    @Value("${email.list.file:email-list.txt}")
    private String emailListFile;

    public ResourcePathStartupLogger(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
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
                  emailList.external={} (exists={})
                  emailList.classpath={}
                  template.index={}
                  static.js.app={}
                  static.css.app={}
                """,
            workingDir,
            resolveApplicationPropertiesLocation(),
            externalEmailListPath(workingDir),
            externalEmailListExists(workingDir),
            describeResource(resourceLoader.getResource("classpath:" + emailListFile)),
            describeResource(resourceLoader.getResource("classpath:/templates/index.html")),
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
