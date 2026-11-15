package com.practice.commerce.controller;

import com.practice.commerce.api.ItemApi;
import com.practice.commerce.model.Item;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CRUD controller for the Item resource. Implements the OpenAPI-generated
 * {@link ItemApi} interface, so HTTP mappings and validation come straight
 * from the spec. Storage is an in-memory map -- swap it for a real repository.
 */
@RestController
public class ItemController implements ItemApi {

    private final Map<Long, Item> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public ResponseEntity<List<Item>> listItems() {
        return ResponseEntity.ok(new ArrayList<>(store.values()));
    }

    @Override
    public ResponseEntity<Item> getItem(Long id) {
        Item entity = store.get(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item " + id + " not found");
        }
        return ResponseEntity.ok(entity);
    }

    @Override
    public ResponseEntity<Item> createItem(Item body) {
        long id = sequence.incrementAndGet();
        body.setId(id);
        store.put(id, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Override
    public ResponseEntity<Item> updateItem(Long id, Item body) {
        if (!store.containsKey(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item " + id + " not found");
        }
        body.setId(id);
        store.put(id, body);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Void> deleteItem(Long id) {
        if (store.remove(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item " + id + " not found");
        }
        return ResponseEntity.noContent().build();
    }
}
