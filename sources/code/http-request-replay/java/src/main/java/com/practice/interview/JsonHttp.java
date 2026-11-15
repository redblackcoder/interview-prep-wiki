package com.practice.interview;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

import com.practice.interview.MyLog.MyRequest;

public class JsonHttp {
    private final String baseUrl = "https://api.example.com";

    private final HttpClient client;

    public JsonHttp() {
        client = HttpClient.newBuilder().build();
    }

    public int makeRequest(MyRequest request) throws Exception {
        var requestBuilder = HttpRequest.newBuilder(new URI(baseUrl + request.url));
        request.headers.forEach((k, v) -> {
            if (!k.equals("Host")) {
                requestBuilder.header(k, v);
            }
        });

        requestBuilder.POST(BodyPublishers.ofString(request.getBody()));
        HttpRequest httpRequest = requestBuilder.build();
        try {
            HttpResponse<String> response = client.send(httpRequest, BodyHandlers.ofString());
            return response.statusCode();
        } catch (Exception exp) {
            return 500;
        }
    }
}
