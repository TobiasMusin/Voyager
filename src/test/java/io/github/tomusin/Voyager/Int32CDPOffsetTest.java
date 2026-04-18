package io.github.tomusin.Voyager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.github.tomusin.voyager.Main;

/**
 * Validates that Int32CDP offset calculation is correct and no heuristic
 * re-sync is needed. A "CDP VALIDATION ERROR" in the log output indicates
 * that a computed offset landed on garbage bytes — i.e. the previous CDP's
 * end-offset was wrong.
 */
class Int32CDPOffsetTest {

    private static final Pattern VALIDATION_ERROR = Pattern.compile("CDP VALIDATION ERROR");

    @Test
    void externalJtFileParsesWithoutValidationErrors() {
        String[] captured = captureLog(() -> Main.main(new String[]{
                "INFO",
                "C:\\EigeneProgramme\\Test-JTs\\small_tire105_1.jt"
        }));
        assertNoValidationErrors(captured);
    }

    @Test
    void workspaceJtFileParsesWithoutValidationErrors() {
        String[] captured = captureLog(() -> Main.main(new String[]{
                "INFO",
                "E:\\JTReaderCollection\\JTReader\\JTReader\\Voyager\\src\\main\\resources\\example_block_jt10.3.jt"
        }));
        assertNoValidationErrors(captured);
    }

    // ---- helpers ----

    private static void assertNoValidationErrors(String[] lines) {
        StringBuilder errors = new StringBuilder();
        for (String line : lines) {
            if (VALIDATION_ERROR.matcher(line).find()) {
                errors.append(line).append('\n');
            }
        }
        assertFalse(errors.length() > 0,
                "Expected zero CDP VALIDATION ERRORs but found:\n" + errors);
    }

    private static String[] captureLog(Runnable task) {
        // tinylog writes to System.out/err – capture both
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(baos);
        System.setOut(capture);
        System.setErr(capture);
        try {
            assertDoesNotThrow(task::run);
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
        return baos.toString().split("\\R");
    }
}
