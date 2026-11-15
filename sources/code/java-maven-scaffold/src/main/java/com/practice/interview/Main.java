package com.practice.interview;

import com.google.common.html.HtmlEscapers;

public class Main {
    public static void main(String... args) {
        System.out.println("Hello world!");
    }

    public static String useGuavaForSomeReason(String input) {
        return HtmlEscapers.htmlEscaper().escape(input);
    }

    public static long factorial(int n) {
        if (n == 1) {
            return 1L;
        }

        return n * factorial(n - 1);
    }
}
