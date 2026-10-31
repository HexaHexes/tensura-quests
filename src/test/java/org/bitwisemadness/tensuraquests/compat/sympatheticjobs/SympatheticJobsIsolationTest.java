package org.bitwisemadness.tensuraquests.compat.sympatheticjobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The INT-002 static check: exactly one package of production code
 * may reference {@code dev.fatsan.sympatheticjobs}.
 *
 * <p>The scan is source-based (a text walk over {@code src/main/java})
 * so it runs without Minecraft or Sympathetic Jobs on the classpath,
 * and it fails loudly if the source tree cannot be found rather than
 * passing vacuously. The gametest source set is <em>deliberately
 * exempt</em> - seeding and verifying job state is the harness's job,
 * as documented on {@code SympatheticJobsGameTests} - which is why
 * this check reads src/main only: production code is the claim
 * INT-002 makes.</p>
 */
public class SympatheticJobsIsolationTest {

    private static final String SJ_PACKAGE = "dev.fatsan.sympatheticjobs";
    private static final String ADAPTER_DIR = "org/bitwisemadness/tensuraquests/compat/sympatheticjobs/";

    @Test
    public void noProductionCodeOutsideTheAdapterPackageTouchesSympatheticJobs() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources()) {
            String normalized = file.toString().replace('\\', '/');
            boolean inAdapterPackage = normalized.contains("/" + ADAPTER_DIR);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(SJ_PACKAGE) && !inAdapterPackage) {
                    violations.add(normalized + ":" + (i + 1));
                }
            }
        }
        assertEquals(List.of(), violations,
            "INT-002: only " + ADAPTER_DIR + " may reference " + SJ_PACKAGE
                + "; move these references behind the adapter");
    }

    @Test
    public void theAdapterPackageIsTheOneTouchingSympatheticJobs() throws IOException {
        List<String> touches = new ArrayList<>();
        for (Path file : javaSources()) {
            String normalized = file.toString().replace('\\', '/');
            if (!normalized.contains("/" + ADAPTER_DIR)) {
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (source.contains(SJ_PACKAGE)) {
                touches.add(normalized);
            }
        }
        assertFalse(touches.isEmpty(),
            "no adapter source references " + SJ_PACKAGE + " - the check would pass vacuously "
                + "if the adapter stopped existing");
        assertTrue(touches.size() <= 3,
            "the adapter package should stay small, found " + touches.size() + " touching files");
    }

    private static List<Path> javaSources() throws IOException {
        Path root = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(root),
            "source root not found at " + root.toAbsolutePath()
                + " - the isolation check must not pass vacuously (working dir "
                + Path.of("").toAbsolutePath() + ")");
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
