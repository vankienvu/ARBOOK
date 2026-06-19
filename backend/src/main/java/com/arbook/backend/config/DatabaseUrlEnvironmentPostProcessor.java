package com.arbook.backend.config;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String explicitUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        String railwayUrl = environment.getProperty("DATABASE_URL");
        if (!StringUtils.hasText(railwayUrl)) {
            railwayUrl = environment.getProperty("DATABASE_PUBLIC_URL");
        }
        if (StringUtils.hasText(explicitUrl) || !StringUtils.hasText(railwayUrl)) {
            return;
        }

        URI uri = URI.create(railwayUrl);
        String userInfo = uri.getUserInfo();
        String username = "";
        String password = "";
        if (StringUtils.hasText(userInfo)) {
            String[] parts = userInfo.split(":", 2);
            username = parts[0];
            password = parts.length > 1 ? parts[1] : "";
        }

        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort() + uri.getPath() + "?options=-c%20TimeZone=UTC";
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", jdbcUrl);
        props.put("spring.datasource.username", username);
        props.put("spring.datasource.password", password);
        environment.getPropertySources().addFirst(new MapPropertySource("databaseUrlConversion", props));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
