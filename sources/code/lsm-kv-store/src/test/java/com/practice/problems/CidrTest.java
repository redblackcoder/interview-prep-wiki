package com.practice.problems;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

public class CidrTest {
    @Test
    public void ipToStr() {
        String ipStr = "255.0.0.7";
        long ipL = Cidr.ipStrToLong(ipStr);
        String ipStrC = Cidr.ipLongToStr(ipL);
        assertEquals(ipStr, ipStrC);
    }

    @Test
    public void ntz() {
        long ip1 = (long) (Math.pow(2, 7) + Math.pow(2, 5) + Math.pow(2, 4));
        System.out.println(Cidr.ipLongToStr(ip1));
        int count = Cidr.ntz(ip1);
        assertEquals(4, count);
    }

    @Test
    public void test1() {
        List<String> cidrs = Cidr.ipToCIDR("255.0.0.7", 10);
        System.out.println(String.join("\n", cidrs));
    }

    @Test
    public void test2() {
        List<String> cidrs = Cidr.ipToCIDR("117.145.102.62", 8);
        System.out.println(String.join("\n", cidrs));
    }
}
