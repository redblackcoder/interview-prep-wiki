package com.practice.lsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    void greet_includesName() {
        assertThat(Main.greet("Ada")).isEqualTo("Hello, Ada!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"world", "Grace", "Linus"})
    void greet_alwaysEndsWithExclamation(String name) {
        assertThat(Main.greet(name)).startsWith("Hello, ").endsWith("!");
    }
}
