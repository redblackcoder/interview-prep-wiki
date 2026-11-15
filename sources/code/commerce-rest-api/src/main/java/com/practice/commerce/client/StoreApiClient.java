package com.practice.commerce.client;

import org.springframework.stereotype.Component;

import com.practice.commerce.http.HttpException;
import com.practice.commerce.http.JsonHttp;
import com.practice.commerce.service.IProductService;

@Component
public class StoreApiClient implements IProductService {
    private static final String URL_BASE = "https://fakestoreapi.com/";

    private final JsonHttp httpClient;

    public StoreApiClient() {
        httpClient = new JsonHttp(URL_BASE);
    }

    public Product getProductById(int id) throws HttpException {
        return httpClient.get(Integer.toString(id), null, Product.class);
    }
}
