package com.github.zeekoe.beaconfluxer;

public class StringUtil {
    public static String padLeft(String in, int size) {
        return " ".repeat(Math.max(0, size - in.length())) + in;
    }
}
