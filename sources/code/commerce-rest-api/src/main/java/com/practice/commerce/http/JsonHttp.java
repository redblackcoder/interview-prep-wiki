package com.practice.commerce.http;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class JsonHttp {
    private final String urlBase;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public JsonHttp(String urlBase) {
        this.urlBase = urlBase;
        requestTimeout = Duration.ofSeconds(5);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        mapper = JsonMapper.builder().disable(FAIL_ON_IGNORED_PROPERTIES).build();
    }

    public <T> T get(String path, Map<String, String> params, Class<T> clazz) throws HttpException {
        URI url;
        try {
            url = new URI(urlBase + path + buildQueryParam(params));
        } catch (URISyntaxException use) {
            throw new HttpException(400, "Malformed url: " + urlBase + path + buildQueryParam(params));
        }

        var request = HttpRequest.newBuilder(url).GET().build();
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return mapper.readValue(response.body(), clazz);
            }
        } catch (IOException ioe) {
            throw new HttpException(500, ioe.getMessage());
        } catch (InterruptedException inte) {
            Thread.currentThread().interrupt();
        }

        return null;
    }

    private static String buildQueryParam(Map<String, String> params) {
        List<String> encoded = params.entrySet().stream()
                .map(e -> encodeUrlPart(e.getKey()) + "=" + encodeUrlPart(e.getValue())).toList();
        return encoded.size() == 0 ? "" : "?" + encoded;
    }

    private static String encodeUrlPart(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
