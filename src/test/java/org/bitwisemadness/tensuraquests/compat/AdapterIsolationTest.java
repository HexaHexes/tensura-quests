package org.bitwisemadness.tensuraquests.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * The adapter-boundary static check: exactly one package of
 * production code may reference each closed-source integration mod -
 * {@code dev.fatsan.sympatheticjobs} (INT-002, issue #11) and
 * {@code io.github.lightman314} (the same discipline for the economy
 * adapter, issue #14).
 *
 * <p>The scan is source-based (a text walk over {@code src/main/java})
 * so it runs without Minecraft or either integration on the classpath,
 * and it fails loudly if the source tree cannot be found rather than
 * passing vacuously. The gametest source set is <em>deliberately
 * exempt</em> - seeding and verifying integration state is the
 * harness's job, as documented on {@code SympatheticJobsGameTests} and
 * {@code LightmansCurrencyGameTests} - which is why this check reads
 * src/main only: production code is the claim both issues make.</p>
 */
public class AdapterIsolationTest {

    /** One forbidden root and the single package allowed to touch it. */
    private record Boundary(String forbiddenRoot, String allowedPackageDir) {
    }

    private static final List<Boundary> BOUNDARIES = List.of(
        new Boundary("dev.fatsan.sympatheticjobs", "org/bitwisemadness/tensuraquests/compat/sympatheticjobs/"),
        new Boundary("io.github.lightman314", "org/bitwisemadness/tensuraquests/compat/lightmanscurrency/"));

    @Test
    public void noProductionCodeTouchesAnIntegrationOutsideItsAdapterPackage() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources()) {
            String normalized = file.toString().replace('\\', '/');
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (Boundary boundary : BOUNDARIES) {
                boolean inAllowedPackage = normalized.contains("/" + boundary.allowedPackageDir());
                if (inAllowedPackage) {
                    continue;
                }
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains(boundary.forbiddenRoot())) {
                        violations.add(boundary.forbiddenRoot() + " at " + normalized + ":" + (i + 1));
                    }
                }
            }
        }
        assertEquals(List.of(), violations,
            "each integration root may only appear in its own adapter package: " + BOUNDARIES);
    }

    @Test
    public void everyAdapterPackageTouchesItsOwnIntegration() throws IOException {
        List<String> deadAdapters = new ArrayList<>();
        for (Boundary boundary : BOUNDARIES) {
            boolean found = false;
            for (Path file : javaSources()) {
                String normalized = file.toString().replace('\\', '/');
                if (!normalized.contains("/" + boundary.allowedPackageDir())) {
                    continue;
                }
                if (Files.readString(file, StandardCharsets.UTF_8).contains(boundary.forbiddenRoot())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                deadAdapters.add(boundary.forbiddenRoot());
            }
        }
        assertEquals(List.of(), deadAdapters,
            "an adapter stopped referencing its integration - the boundary check would pass vacuously");
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
