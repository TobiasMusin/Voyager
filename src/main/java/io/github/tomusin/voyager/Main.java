package io.github.tomusin.voyager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.tinylog.configuration.Configuration;

import io.github.tomusin.voyager.readers.JTReader;

/**
 * Hello world!
 *
 */
public class Main {
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("Usage: java -jar app.jar <fileOrDir1> [fileOrDir2 ...]");
			System.exit(1);
		}

		String levelStr;
		if ("INFO".equals(args[0])) {
			levelStr = System.getProperty("log.level", "INFO");
		} else {
			levelStr = System.getProperty("log.level", "WARN");
		}

		try {
			Class.forName("org.tinylog.Configuration");
			Configuration.set("level", levelStr);
		} catch (ClassNotFoundException ignored) {
			// tinylog not present: skip config
		}

		// Main run
		prepareFiles(args);
	}

	private static void prepareFiles(String[] args) {
		Set<String> filePaths = new LinkedHashSet<>();

		boolean useParallel = args.length > 0 && "-parallel".equalsIgnoreCase(args[args.length - 1]);
		int argLimit = useParallel ? args.length - 1 : args.length;

		// Collect files and directories
		for (int i = 0; i < argLimit; i++) {
			Path path = Path.of(args[i]);
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
				System.out.println("Skipping: " + args[i] + " (not a .jt file or directory)");
			}
		}

		// Read JT files
		readFiles(filePaths, useParallel);
	}

	private static void readFiles(Set<String> filePaths, boolean useParallel) {
		if (useParallel) {
			List<Thread> threads = new ArrayList<>();
			for (String filePath : filePaths) {
				Thread t = Thread.ofVirtual().start(() -> {
					try {
						System.out.println("Reading: " + filePath);
						Path filename = Path.of(filePath);
						JTReader jtReader = new JTReader();
						jtReader.startReading(filename);
					} catch (Exception e) {
						System.err.println("Failed to read: " + filePath + " (" + e.getMessage() + ")");
					}
				});
				threads.add(t);
			}
			// Wait for all threads to complete
			for (Thread t : threads) {
				try {
					t.join();
				} catch (InterruptedException e) {
					System.err.println("Thread interrupted: " + e.getMessage());
					Thread.currentThread().interrupt();
				}
			}
		} else {
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
	}

}
