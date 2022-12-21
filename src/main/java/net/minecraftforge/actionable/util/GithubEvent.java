package net.minecraftforge.actionable.util;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum GithubEvent {
    ISSUE_COMMENT,


    UNKNOWN;

    public static final Map<String, GithubEvent> BY_ID = Stream.of(values())
            .collect(Collectors.toMap(it -> it.toString().toLowerCase(Locale.ROOT), Function.identity()));
}
