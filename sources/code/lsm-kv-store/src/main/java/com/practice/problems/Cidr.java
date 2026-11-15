package com.practice.problems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * https://leetcode.com/problems/ip-to-cidr
 * 
 * Input: ip = "255.0.0.7", n = 10
 * Output: ["255.0.0.7/32","255.0.0.8/29","255.0.0.16/32"]
 */
public class Cidr {
    public static List<String> ipToCIDR(String ip, int n) {
        long ipL = ipStrToLong(ip);
        int captured = 0;
        List<String> cidr = new ArrayList<>();
        while (captured < n) {
            int ntzC = ntz(ipL);
            for (int i = ntzC; i >= 0; i--) {
                int covered = (int) Math.pow(2, i);
                if (n - captured >= covered) {
                    String cidrIp = ipLongToStr(ipL);
                    cidr.add(cidrIp + "/" + (32 - i));
                    captured += covered;
                    ipL += covered;
                    break;
                }
            }
        }

        return cidr;
    }

    // number of trailing zeroes
    // 0010111101100 -> 2
    static int ntz(long val) {
        int count = 0;
        long mask = 1;
        while ((val & mask) == 0) {
            count++;
            val >>>= 1;
        }

        return count;
    }

    static long ipStrToLong(String ipStr) {
        long ipL = 0;
        String[] parts = ipStr.split("\\.");
        for (int i = 0; i < 4; i++) {
            String p = parts[3 - i];
            long v = Long.parseLong(p);
            ipL += (v << (8 * i));
        }

        return ipL;
    }

    static String ipLongToStr(long ipL) {
        long mask = (long) (Math.pow(2, 8)) - 1;
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            parts.add(Long.toString((ipL >> (8 * i)) & mask));
        }

        Collections.reverse(parts);
        return String.join(".", parts);
    }
}
