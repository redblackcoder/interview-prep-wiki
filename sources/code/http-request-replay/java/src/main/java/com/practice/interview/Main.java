package com.practice.interview;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.GsonBuilder;

public class Main {
	public static void main(String... args) {
		List<MyLog> logs = parseLogs();
		var c = new JsonHttp();
		logs.forEach(l -> {
			try {
				int code = c.makeRequest(l.request);
				System.out.println(String.format("Expected: %d, actual: %d", l.response.code, code));
			} catch (Exception exp) {
				System.err.println("err");
			}
		});
	}

	public static List<Integer> getCodes() {
		List<Integer> codes = new ArrayList<>();
		List<MyLog> logs = parseLogs();
		var c = new JsonHttp();
		logs.forEach(l -> {
			try {
				int code = c.makeRequest(l.request);
				codes.add(code);
				System.out.println(String.format("Expected: %d, actual: %d", l.response.code, code));
			} catch (Exception exp) {
				System.err.println("err");
			}
		});

		return codes;
	}

	public static List<MyLog> parseLogs() {
		Parser parser = new Parser();
		List<MyLog> logs = parser
				.parse("../requestlog-charges.json");
		System.out.println(logs.size());
		return logs;
	}
}
