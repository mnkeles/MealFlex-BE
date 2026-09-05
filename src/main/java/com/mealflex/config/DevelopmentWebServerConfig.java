package com.mealflex.config;

import org.apache.coyote.http11.Http11Nio2Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class DevelopmentWebServerConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> developmentTomcatProtocol() {
        return factory -> factory.setProtocol(Http11Nio2Protocol.class.getName());
    }
}
