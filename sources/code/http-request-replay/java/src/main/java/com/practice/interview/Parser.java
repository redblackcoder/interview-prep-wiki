package com.practice.interview;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Parser {
    private final Gson gson;

    public Parser() {
        gson = new Gson();
    }

    public List<MyLog> parse(String filePath) {
        try {
            String content = Files.readString(Path.of(filePath));
            return parseContent(content);
        } catch (IOException ioe) {
            System.err.printf("Failed to parse the file %s", filePath);
            return Collections.EMPTY_LIST;
        }
    }

    public List<MyLog> parseContent(String content) {
        Type listType = new TypeToken<List<MyLog>>() {
        }.getType();
        return gson.fromJson(content, listType);
    }
}
