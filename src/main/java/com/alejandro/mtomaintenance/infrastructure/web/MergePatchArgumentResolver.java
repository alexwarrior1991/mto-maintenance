package com.alejandro.mtomaintenance.infrastructure.web;

import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lee un cuerpo {@code application/merge-patch+json} como {@link MergePatch}: el record de la
 * modificacion, validado igual que un {@code @Valid @RequestBody}, y los campos que venian a
 * {@code null}.
 *
 * <p>Un campo que el record no tiene es 400. En un PUT se ignora, pero aqui un {@code null} con el
 * nombre mal escrito no vaciaria nada, y quien llama no se enteraria. {@code "version": null} no
 * vacia nada: es no mandar version.</p>
 */
public class MergePatchArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String VERSION = "version";

    private final JsonMapper jsonMapper;

    public MergePatchArgumentResolver(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return MergePatch.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        ServletServerHttpRequest message = new ServletServerHttpRequest(request);
        Class<?> type = ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve();
        if (type == null || !type.isRecord()) {
            throw new IllegalStateException("MergePatch needs a record type argument: " + parameter);
        }

        JsonNode body;
        try {
            body = jsonMapper.readTree(request.getInputStream());
        } catch (JacksonException exception) {
            throw new HttpMessageNotReadableException("Malformed merge patch: " + exception.getOriginalMessage(), exception, message);
        }
        if (body == null || !body.isObject()) {
            throw new HttpMessageNotReadableException("A merge patch is a JSON object", message);
        }

        Set<String> fields = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
        Set<String> cleared = new LinkedHashSet<>();
        for (Map.Entry<String, JsonNode> field : body.properties()) {
            if (!fields.contains(field.getKey())) {
                throw new ValidationException("Unknown field '" + field.getKey() + "' in the merge patch");
            }
            if (field.getValue().isNull() && !VERSION.equals(field.getKey())) {
                cleared.add(field.getKey());
            }
        }

        Object values;
        try {
            values = jsonMapper.treeToValue(body, type);
        } catch (JacksonException exception) {
            throw new HttpMessageNotReadableException("Malformed merge patch: " + exception.getOriginalMessage(), exception, message);
        }
        if (parameter.hasParameterAnnotation(Valid.class) || parameter.hasParameterAnnotation(Validated.class)) {
            String name = parameter.getParameterName() == null ? "request" : parameter.getParameterName();
            WebDataBinder binder = binderFactory.createBinder(webRequest, values, name);
            binder.validate();
            if (binder.getBindingResult().hasErrors()) {
                throw new MethodArgumentNotValidException(parameter, binder.getBindingResult());
            }
        }
        return new MergePatch<>(values, cleared);
    }
}
