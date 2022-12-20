package net.minecraftforge.actionable.util;

import com.fasterxml.jackson.databind.util.ArrayIterator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Jsons {
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().create();

    public static <T> T get(JsonObject object, String name, Class<? extends T> type) {
        return GSON.fromJson(object.getAsJsonObject(name), type);
    }

    public static <T> T get(Path path, Class<? extends T> type) throws IOException  {
        try (final var reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static <T> T at(JsonObject object, String path, Class<? extends T> type) {
        final List<String> nests = Arrays.asList(path.split("\\."));
        Collections.reverse(nests);

        JsonElement current = object;
        for (final String nest : nests) {
            current = current.isJsonObject() ? current.getAsJsonObject().get(nest) : null;
            if (current == null) return null;
        }

        return GSON.fromJson(current, type);
    }

    public static String toString(JsonElement element) {
        if (element.isJsonPrimitive()) return element.toString();
        return GSON.toJson(element);
    }
}
