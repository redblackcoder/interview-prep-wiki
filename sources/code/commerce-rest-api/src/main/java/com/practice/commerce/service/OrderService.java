package com.practice.commerce.service;

import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.practice.commerce.model.Invoice;
import com.practice.commerce.model.Order;

@Service
public class OrderService {
    private final IProductService productService;

    @Autowired
    public OrderService(IProductService productService) {
        this.productService = productService;
    }

    public List<Order> listOrders() {
        throw new NotImplementedException();
    }

    public Invoice placeOrder(Order order) {
        throw new NotImplementedException();
    }
}
