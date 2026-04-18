package io.github.tomusin.Voyager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import io.github.tomusin.voyager.Main;

class MainDebugArgsTest {

    @Test
    void runsWithDebugArgsForExternalJtFile() {
        assertDoesNotThrow(() -> Main.main(new String[] {
                "INFO",
                "C:\\EigeneProgramme\\Test-JTs\\small_tire105_1.jt"
        }));
    }

    @Test
    void runsWithDebugArgsForWorkspaceJtFile() {
        assertDoesNotThrow(() -> Main.main(new String[] {
                "INFO",
                "E:\\JTReaderCollection\\JTReader\\JTReader\\Voyager\\src\\main\\resources\\example_block_jt10.3.jt"
        }));
    }
}
