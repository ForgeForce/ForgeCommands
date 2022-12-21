package net.minecraftforge.actionable.util;

import com.fasterxml.jackson.databind.JsonNode;

public class Jsons {
    public static JsonNode at(JsonNode node, String path) {
        final String[] paths = path.split("\\.");
        for (int i = path.length() - 1; i >= 0; i--) {
            node = node.get(paths[i]);
        }
        return node;
    }
}
