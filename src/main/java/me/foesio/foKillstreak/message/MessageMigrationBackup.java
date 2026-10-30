package me.foesio.foKillstreak.message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Keeps the original owner message bytes before the first versioned public-text update. */
public final class MessageMigrationBackup {
    private static final String TARGET_SUFFIX = "-to-v4-backup";

    private MessageMigrationBackup() {
    }

    public static void prepare(Path messages, int previousVersion, boolean freshInstall) throws IOException {
        if (freshInstall || previousVersion >= 4 || !Files.isRegularFile(messages)) {
            return;
        }
        String prefix = messages.getFileName() + ".gui-v";
        try (Stream<Path> files = Files.list(messages.getParent())) {
            if (files.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().startsWith(prefix)
                    && path.getFileName().toString().endsWith(TARGET_SUFFIX))) {
                return;
            }
        }
        Path backup = messages.resolveSibling(prefix + Math.max(0, previousVersion) + TARGET_SUFFIX);
        Files.copy(messages, backup);
    }
}
