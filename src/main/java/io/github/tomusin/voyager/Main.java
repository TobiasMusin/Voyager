package io.github.tomusin.voyager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.github.tomusin.voyager.readers.JTReader;

/**
 * Hello world!
 *
 */
public class Main 
{
	public static void main(String[] args) {
	    if (args.length == 0) {
	        System.out.println("Usage: java -jar app.jar <fileOrDir1> [fileOrDir2 ...]");
	        System.exit(1);
	    }

	    // Start preloading early, but only if not doing a PGO-training run
	    if (!"-pgoRun".equals(args[0])) {
	        preloadFilesInBackground(args);
	    }

	    // Initialize logging
	    String levelStr = "WARN";
	    if ("INFO".equals(args[0])) {
	        levelStr = System.getProperty("log.level", "INFO");
	    } else {
	        levelStr = System.getProperty("log.level", "WARN");
	    }
	    Level level = Level.toLevel(levelStr);
	    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
	    context.getLogger("ROOT").setLevel(level);

	    // PGO batch run
	    if ("-pgoRun".equals(args[0])) {
	        for (int i = 0; i < 50000; i++) {
	            startReading(args);
	        }
	    }

	    // Main run
	    startReading(args);
	}

private static void startReading(String[] args) {
	Set<String> filePaths = new LinkedHashSet<>();

	for (String arg : args) {
	    Path path = Path.of(arg);
	    if (Files.isDirectory(path)) {
	        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, "*.jt")) {
	            for (Path file : stream) {
	                filePaths.add(file.toAbsolutePath().toString());
	            }
	        } catch (IOException e) {
	            System.err.println("Error reading directory: " + path + " (" + e.getMessage() + ")");
	        }
	    } else if (Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".jt")) {
	        filePaths.add(path.toAbsolutePath().toString());
	    } else {
	        System.out.println("Skipping: " + arg + " (not a .jt file or directory)");
	    }
	}

	for (String filePath : filePaths) {
	    try {
	        System.out.println("Reading: " + filePath);
	        Path filename = Path.of(filePath);
	        JTReader jtReader = new JTReader();
	        jtReader.startReading(filename);
	    } catch (Exception e) {
	        System.err.println("Failed to read: " + filePath + " (" + e.getMessage() + ")");
	    }
	}
}

private static void preloadFilesInBackground(String[] args) {
    new Thread(() -> {
        for (String arg : args) {
            Path path = Path.of(arg);
            try {
                if (Files.isRegularFile(path) && path.toString().endsWith(".jt")) {
                    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                        // Touch first few pages (or full file if small)
                        ByteBuffer buf = ByteBuffer.allocate(1024 * 1024); // 1 MB
                        while (channel.read(buf) > 0) {
                            buf.clear();
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Preload failed: " + path + " (" + e.getMessage() + ")");
            }
        }
    }, "PreloadThread").start();
}
	
}
