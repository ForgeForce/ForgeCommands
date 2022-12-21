package org.kohsuke.github;

import java.util.Locale;

public enum LockReason {
    OFF_TOPIC,
    TOO_HEATED {
        @Override
        public String toString() {
            return "too heated";
        }
    },
    RESOLVED,
    SPAM;

    @Override
    public String toString() {
        return super.toString().toLowerCase(Locale.ROOT);
    }
}
