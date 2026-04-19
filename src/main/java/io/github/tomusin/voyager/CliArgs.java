package io.github.tomusin.voyager;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public record CliArgs(Mode mode, String logLevel, boolean parallel, boolean verbose, String outputFile, Set<String> filePaths) {

	public enum Mode { PARSE, RENDER, EXPORT }
	
	static CliArgs parse(String[] args) {
		
		if (args.length == 0) {
			printUsage();
			System.exit(1);
		}
		
		Mode mode = Mode.PARSE;
		String logLevel = "WARN";
		boolean parallel = false;
		boolean verbose = false;
		String outputFile = null;
		Set<String> filePaths = new LinkedHashSet<>();

		for (int i = 0; i < args.length; i++) {
			String flag = args[i].toLowerCase();
			switch (flag) {
				case "--help", "-h" -> { printUsage(); System.exit(0); }
				case "--render", "-r" -> mode = Mode.RENDER;
				case "--export", "-e" -> mode = Mode.EXPORT;
				case "--parse", "-p" -> mode = Mode.PARSE;
				case "--parallel" -> parallel = true;
				case "--verbose", "-v" -> { logLevel = "INFO"; verbose = true; }
				case "--log-level" -> {
					if (i + 1 < args.length) { logLevel = args[++i].toUpperCase(); verbose = !"WARN".equals(logLevel) && !"ERROR".equals(logLevel); }
					else { System.err.println("--log-level requires a level (e.g., INFO, DEBUG, WARN)"); System.exit(1); }
				}
				case "--output", "-o" -> {
					if (i + 1 < args.length) outputFile = args[++i];
					else { System.err.println("--output requires a file path"); System.exit(1); }
				}
				default -> {
					if (args[i].startsWith("-")) {
						System.err.println("Unknown option: " + args[i]);
						printUsage();
						System.exit(1);
					}
					Path path = Path.of(args[i]);
					if (Files.isDirectory(path)) {
						try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, "*.jt")) {
							for (Path file : stream) filePaths.add(file.toAbsolutePath().toString());
						} catch (IOException ex) {
							System.err.println("Error reading directory: " + path + " (" + ex.getMessage() + ")");
						}
					} else if (Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".jt")) {
						filePaths.add(path.toAbsolutePath().toString());
					} else {
						System.err.println("Skipping: " + args[i] + " (not a .jt file or directory)");
					}
				}
			}
		}

		if (filePaths.isEmpty()) {
			System.err.println("No .jt files specified.");
			printUsage();
			System.exit(1);
		}

		return new CliArgs(mode, logLevel, parallel, verbose, outputFile, filePaths);
	}
	
	private static void printUsage() {
		System.out.println("""
			Usage: voyager [options] <file.jt | directory> [...]

			Modes (default: --parse):
			  -p, --parse           Parse and print geometry summary only
			  -r, --render          Parse and open 3D viewer
			  -e, --export          Parse and export to glTF file

			Options:
			  -h, --help            Show this help message
			  -v, --verbose         Enable INFO-level logging
			  --log-level LEVEL     Set log level (e.g., DEBUG, INFO, WARN, ERROR)
			  -o, --output PATH     Output file or directory for --export (default: derived from input)
			  --parallel            Read multiple files in parallel
			""");
	}
}
