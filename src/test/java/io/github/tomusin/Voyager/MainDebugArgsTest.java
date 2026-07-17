package io.github.tomusin.Voyager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.github.tomusin.voyager.CliArgs;
import io.github.tomusin.voyager.Main;

class MainDebugArgsTest {

    private static final Path EXAMPLE_FILE = workspacePath("src", "main", "resources", "example_block_jt10.3.jt");
    private static final Path EXAMPLE_DIRECTORY = workspacePath("src", "main", "resources", "10.6");

    @Test
    void parsesExampleJtFileWithoutThrowing() {
        assertDoesNotThrow(() -> Main.run(buildParseArgs(
                "INFO",
                EXAMPLE_FILE
        )));
    }

    @Test
    void parsesExampleJtFileFromWorkspaceResourcesWithoutThrowing() {
        assertDoesNotThrow(() -> Main.run(buildParseArgs(
                "INFO",
                EXAMPLE_FILE
        )));
    }

    @Test
    void parsesExampleJtDirectoryWithoutThrowing() {
        assertDoesNotThrow(() -> Main.run(buildParseArgs(
                "INFO",
                EXAMPLE_DIRECTORY
        )));
    }
    
    private static CliArgs buildParseArgs(String logLevel, Path inputPath) {
        Set<String> filePaths = new LinkedHashSet<>();
        
        if (Files.isDirectory(inputPath)) {
            // Recursively scan directory for all .jt files
            try (Stream<Path> walk = Files.walk(inputPath)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".jt"))
                    .forEach(p -> filePaths.add(p.toAbsolutePath().toString()));
            } catch (IOException e) {
                System.err.println("Error scanning directory: " + inputPath + " (" + e.getMessage() + ")");
            }
        } else {
            filePaths.add(inputPath.toAbsolutePath().toString());
        }
        
        return new CliArgs(CliArgs.Mode.PARSE, logLevel, false, true, null, filePaths);
    }

    private static Path workspacePath(String first, String... more) {
        return Path.of(first, more).toAbsolutePath().normalize();
    }
}