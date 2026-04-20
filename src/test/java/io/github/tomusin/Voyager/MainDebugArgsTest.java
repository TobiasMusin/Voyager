package io.github.tomusin.Voyager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.LinkedHashSet;
import java.util.Set;

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

    private static CliArgs buildCliArgs(String logLevel, String filePath) {
        Set<String> filePaths = new LinkedHashSet<>();
        filePaths.add(filePath);
        return new CliArgs(CliArgs.Mode.PARSE, logLevel, false, true, null, filePaths);
    }
}