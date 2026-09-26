package com.alejandro.mtomaintenance.configuration.web;

import com.alejandro.mtomaintenance.infrastructure.web.MergePatchArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** Los PATCH {@code application/merge-patch+json} llegan a los controladores como {@code MergePatch}. */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final JsonMapper jsonMapper;

    public WebMvcConfiguration(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new MergePatchArgumentResolver(jsonMapper));
    }
}
