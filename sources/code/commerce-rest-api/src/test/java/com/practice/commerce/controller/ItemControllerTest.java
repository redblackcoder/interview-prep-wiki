package com.practice.commerce.controller;

import com.practice.commerce.model.Item;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link ItemController}'s CRUD behaviour. Exercises the
 * controller directly (no Spring context) with JUnit 5 + AssertJ, so it is
 * fast. For full HTTP/validation coverage, add a @WebMvcTest with MockMvc.
 */
class ItemControllerTest {

    private final ItemController controller = new ItemController();

    private Item sample(String name) {
        Item entity = new Item();
        entity.setName(name);
        return entity;
    }

    @Test
    void create_thenGet_returnsEntityWithGeneratedId() {
        ResponseEntity<Item> created = controller.createItem(sample("first"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        Long id = created.getBody().getId();
        assertThat(id).isNotNull();

        ResponseEntity<Item> fetched = controller.getItem(id);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().getName()).isEqualTo("first");
    }

    @Test
    void list_reflectsCreatedEntities() {
        controller.createItem(sample("a"));
        controller.createItem(sample("b"));
        ResponseEntity<List<Item>> all = controller.listItems();
        assertThat(all.getBody()).hasSize(2);
    }

    @Test
    void update_changesStoredEntity() {
        Long id = controller.createItem(sample("old")).getBody().getId();
        controller.updateItem(id, sample("new"));
        assertThat(controller.getItem(id).getBody().getName()).isEqualTo("new");
    }

    @Test
    void delete_thenGet_throwsNotFound() {
        Long id = controller.createItem(sample("gone")).getBody().getId();
        assertThat(controller.deleteItem(id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThatThrownBy(() -> controller.getItem(id))
                .isInstanceOf(ResponseStatusException.class);
    }
}
