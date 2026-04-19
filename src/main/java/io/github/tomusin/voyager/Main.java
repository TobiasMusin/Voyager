package io.github.tomusin.voyager;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.tinylog.configuration.Configuration;

import io.github.tomusin.lodDataRecords.CompressedVertexCoordinateArrayRecord;
import io.github.tomusin.voyager.datastructures.TreeNode;
import io.github.tomusin.voyager.export.GltfExporter;
import io.github.tomusin.voyager.readers.JTReader;
import io.github.tomusin.voyager.viewer.JTGeometryViewer;

public class Main {

	private enum Mode { PARSE, RENDER, EXPORT }

	public static void main(String[] args) {
		if (args.length == 0) {
			printUsage();
			System.exit(1);
		}

		// Parse flags
		Mode mode = Mode.PARSE;
		String logLevel = "WARN";
		boolean useParallel = false;
		String outputFile = "output.gltf";
		Set<String> filePaths = new LinkedHashSet<>();

		for (int i = 0; i < args.length; i++) {
			switch (args[i].toLowerCase()) {
				case "--render", "-r" -> mode = Mode.RENDER;
				case "--export", "-e" -> mode = Mode.EXPORT;
				case "--parse", "-p" -> mode = Mode.PARSE;
				case "--parallel" -> useParallel = true;
				case "--verbose", "-v" -> logLevel = "INFO";
				case "--output", "-o" -> {
					if (i + 1 < args.length) outputFile = args[++i];
					else { System.err.println("--output requires a file path"); System.exit(1); }
				}
				default -> {
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

		// Configure logging
		try {
			Class.forName("org.tinylog.Configuration");
			Configuration.set("level", logLevel);
		} catch (ClassNotFoundException ignored) {}

		run(filePaths, mode, useParallel, outputFile);
	}

	private static void printUsage() {
		System.out.println("""
			Usage: voyager [options] <file.jt | directory> [...]

			Modes (default: --parse):
			  -p, --parse     Parse and print geometry summary only
			  -r, --render    Parse and open 3D viewer
			  -e, --export    Parse and export to glTF file

			Options:
			  -v, --verbose   Enable INFO-level logging
			  -o, --output F  Output file path for --export (default: output.gltf)
			  --parallel      Read multiple files in parallel
			""");
	}

	private static void run(Set<String> filePaths, Mode mode, boolean useParallel, String outputFile) {
		JTReader jtReader = new JTReader();

		// Read files
		if (useParallel) {
			List<Thread> threads = new ArrayList<>();
			for (String filePath : filePaths) {
				Thread t = Thread.ofVirtual().start(() -> {
					System.out.println("Reading: " + filePath);
					jtReader.startReading(Path.of(filePath));
				});
				threads.add(t);
			}
			for (Thread t : threads) {
				try { t.join(); } catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		} else {
			for (String filePath : filePaths) {
				System.out.println("Reading: " + filePath);
				jtReader.startReading(Path.of(filePath));
			}
		}

		List<TreeNode> roots = jtReader.getRootNodes();
		if (roots.isEmpty()) {
			System.out.println("No scene graph found.");
			return;
		}

		// Print geometry summary (always)
		for (TreeNode root : roots) {
			for (TreeNode node : root.findNodesWithGeometry()) {
				float[][] coords = node.getVertexCoordinates();
				System.out.printf("Node %d (%s): %d vertices%n",
						node.objectID, node.nodeName, coords != null ? coords[0].length : 0);
			}
		}

		switch (mode) {
			case RENDER -> {
				// Find first node with geometry and render it
				for (TreeNode root : roots) {
					List<TreeNode> geoNodes = root.findNodesWithGeometry();
					if (!geoNodes.isEmpty()) {
						CompressedVertexCoordinateArrayRecord coordArray = geoNodes.getFirst().getCompressedVertexCoordinateArray();
						if (coordArray != null) {
							JTGeometryViewer.show(coordArray);
							return;
						}
					}
				}
				System.out.println("No renderable geometry found.");
			}
			case EXPORT -> {
				try {
					GltfExporter.export(roots, Path.of(outputFile));
				} catch (IOException e) {
					System.err.println("Failed to export glTF: " + e.getMessage());
				}
			}
			case PARSE -> { /* already printed summary above */ }
		}
	}
}