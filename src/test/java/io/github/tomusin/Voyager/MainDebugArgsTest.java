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

    @Test
    void runsWithDebugArgsForExternalJtFile() {
        assertDoesNotThrow(() -> Main.run(buildCliArgs(
                "INFO",
                "C:\\EigeneProgramme\\Test-JTs\\small_tire105_1.jt"
        )));
    }

    @Test
    void runsWithDebugArgsForWorkspaceJtFile() {
        assertDoesNotThrow(() -> Main.run(buildCliArgs(
                "INFO",
                "E:\\JTReaderCollection\\JTReader\\JTReader\\Voyager\\src\\main\\resources\\example_block_jt10.3.jt"
        )));
    }

    @Test
    void runsAllWithDebugArgsForWorkspaceJtFile() {
        assertDoesNotThrow(() -> Main.run(buildCliArgs(
                "INFO",
                "C:\\EigeneProgramme\\Test-JTs\\JT"
        )));
    }
    
    private static CliArgs buildCliArgs(String logLevel, String filePath) {
        Set<String> filePaths = new LinkedHashSet<>();
        Path path = Path.of(filePath);
        
        if (Files.isDirectory(path)) {
            // Recursively scan directory for all .jt files
            try (Stream<Path> walk = Files.walk(path)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".jt"))
                    .forEach(p -> filePaths.add(p.toAbsolutePath().toString()));
            } catch (IOException e) {
                System.err.println("Error scanning directory: " + path + " (" + e.getMessage() + ")");
            }
        } else {
            filePaths.add(filePath);
        }
        
        return new CliArgs(CliArgs.Mode.PARSE, logLevel, false, true, null, filePaths);
    }
}