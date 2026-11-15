package com.practice.interview;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.List;

public class MainTest {
	@Test
	public void testParsing() {
		List<MyLog> logs = Main.parseLogs();
		assertEquals(3, logs.size());
	}

	@Test
	public void testRequests() {
		List<Integer> codes = Main.getCodes();
		codes.forEach(c -> {
			System.out.println(c);
		});
	}
}
