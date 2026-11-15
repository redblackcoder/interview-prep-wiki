package com.practice.commerce.service;

import com.practice.commerce.client.Product;
import com.practice.commerce.http.HttpException;

public interface IProductService {
    public Product getProductById(int id) throws HttpException;
}
