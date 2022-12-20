package net.minecraftforge.actionable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println(Files.readString(Path.of(System.getenv("GITHUB_EVENT_PATH"))));;
        System.out.println("\n");

        System.out.println("Args: " + Arrays.toString(args));
        System.getenv().forEach((key, val) -> {
            System.out.println("Key: " + key + ", Val: " + val);
        });
    }
}
