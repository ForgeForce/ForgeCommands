package net.minecraftforge.actionable;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        System.out.println("Args: " + Arrays.toString(args));
        System.getenv().forEach((key, val) -> {
            System.out.println("Key: " + key + ", Val: " + val);
        });
    }
}
