package com.edgareldy.springintegrationtutorial.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.util.FileSystemUtils;

/**
 * The directories of the test classes that assert on outbound files: property overrides giving them a
 * context of their own, with its own incoming and outgoing folders, and a way to empty those folders.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// Every cached context keeps its poller running and writes its outbound files, and each context has its own
// database, so order ids repeat from one context to another. Test classes that look for "order-<id>.txt"
// therefore share one context (the same property overrides) with directories no other context uses.
public final class RoutingTestDirectories {

    /** Incoming directory override, so no other context's poller competes for the dropped files. */
    public static final String INCOMING = "orders.directories.incoming=target/test-orders/routing/incoming-orders";

    /** Outgoing directory override; confirmations, reviews and rejections follow it. */
    public static final String OUTGOING = "orders.directories.outgoing=target/test-orders/routing/outgoing-orders";

    private RoutingTestDirectories() {
    }

    /**
     * Deletes everything inside each directory that exists, keeping the directory itself: a watched
     * directory must not disappear under its poller.
     *
     * @param directories the directories to empty
     * @throws IOException if a file cannot be deleted
     */
    public static void empty(Path... directories) throws IOException {
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> children = Files.list(directory)) {
                for (Path child : children.toList()) {
                    FileSystemUtils.deleteRecursively(child);
                }
            }
        }
    }
}
