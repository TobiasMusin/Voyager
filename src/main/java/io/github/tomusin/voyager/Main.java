package io.github.tomusin.voyager;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.tinylog.Logger;
import org.tinylog.configuration.Configuration;

import io.github.tomusin.voyager.CliArgs.Mode;
import io.github.tomusin.voyager.datastructures.TreeNode;
import io.github.tomusin.voyager.export.GltfExporter;
import io.github.tomusin.voyager.readers.JTReader;
import io.github.tomusin.voyager.viewer.JTGeometryViewer;

public class Main {

	private record FileResult(String path, JTReader reader) {}

	public static void main(String[] args) {

		CliArgs cli = CliArgs.parse(args);

		// Configure logging early so subsequent messages use Logger
		Configuration.set("level", cli.logLevel());

		if (cli.outputFile() != null) {
			Path outPath = Path.of(cli.outputFile());
			boolean treatAsDir = cli.filePaths().size() > 1 || cli.outputFile().endsWith("/") || cli.outputFile().endsWith("\\") || Files.isDirectory(outPath);
			if (treatAsDir && !Files.isDirectory(outPath)) {
				Path parent = outPath.getParent();
				if (parent != null && Files.isDirectory(parent)) {
					try { Files.createDirectory(outPath); }
					catch (IOException e) { Logger.error("Failed to create output directory: {}", e.getMessage()); System.exit(1); }
				} else {
					Logger.error("--output parent directory does not exist: {}", outPath);
					System.exit(1);
				}
			}
		}

		System.exit(run(cli));
	}

	public static int run(CliArgs cli) {
		// When running as a native image, set LWJGL library path to the directory
		// containing the executable so it can find the shipped DLLs.
		if (System.getProperty("org.graalvm.nativeimage.imagecode") != null
				&& System.getProperty("org.lwjgl.librarypath") == null) {
			try {
				Path exeDir = Path.of(ProcessHandle.current().info().command().orElse(".")).getParent();
				if (exeDir != null) {
					System.setProperty("org.lwjgl.librarypath", exeDir.toAbsolutePath().toString());
				}
			} catch (Exception ignored) { /* best-effort */ }
		}

		List<FileResult> results = new ArrayList<>();
		List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

		if (cli.parallel()) {
			List<Thread> threads = new ArrayList<>();
			for (String filePath : cli.filePaths()) {
				JTReader reader = new JTReader();
				results.add(new FileResult(filePath, reader));
				Thread t = Thread.ofVirtual().start(() -> {
					try {
						Logger.info("Reading: {}", filePath);
						reader.startReading(Path.of(filePath));
					} catch (Exception e) {
						Logger.error(e, "Failed to read: {}", filePath);
						errors.add(e);
					}
				});
				threads.add(t);
			}
			for (Thread t : threads) {
				try { t.join(); } catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		} else {
			for (String filePath : cli.filePaths()) {
				Logger.info("Reading: {}", filePath);
				JTReader reader = new JTReader();
				try {
					reader.startReading(Path.of(filePath));
				} catch (Exception e) {
					Logger.error(e, "Failed to read: {}", filePath);
					errors.add(e);
					continue;
				}
				results.add(new FileResult(filePath, reader));
			}
		}

		if (!errors.isEmpty())
			Logger.warn("{} file(s) failed to read", errors.size());

		List<TreeNode> allRoots = new ArrayList<>();
		for (FileResult r : results) allRoots.addAll(r.reader().getRootNodes());

		if (allRoots.isEmpty()) {
			Logger.warn("No scene graph found.");
			return 1;
		}

		// Collect geometry nodes once
		List<TreeNode> allGeoNodes = new ArrayList<>();
		for (TreeNode root : allRoots) allGeoNodes.addAll(root.findNodesWithGeometry());

		// Print geometry summary in PARSE mode, or when verbose
		if (cli.mode() == Mode.PARSE || cli.verbose()) {
			for (TreeNode node : allGeoNodes) {
				float[][] coords = node.getVertexCoordinates();
				System.out.printf("Node %d (%s): %d vertices%n",
						node.objectID, node.nodeName, coords != null ? coords[0].length : 0);
			}
		}

		switch (cli.mode()) {
			case RENDER -> {
				if (!allGeoNodes.isEmpty()) {
					JTGeometryViewer.showAll(allGeoNodes);
				} else {
					Logger.warn("No renderable geometry found.");
					return 1;
				}
			}
			case EXPORT -> {
				Path outDir = cli.outputFile() != null ? Path.of(cli.outputFile()) : null;
				boolean outIsDir = outDir != null && Files.isDirectory(outDir);
				if (outDir != null && !outIsDir && results.size() == 1) {
					try {
						GltfExporter.export(allRoots, outDir);
					} catch (IOException e) {
						Logger.error(e, "Failed to export glTF: {}", e.getMessage());
						return 1;
					}
				} else {
					for (FileResult r : results) {
						List<TreeNode> roots = r.reader().getRootNodes();
						if (roots.isEmpty()) continue;
						Path inputPath = Path.of(r.path());
						String baseName = inputPath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
						Path parent = outDir != null ? outDir : inputPath.getParent();
						Path gltfPath = parent.resolve(baseName + ".gltf");
						try {
							GltfExporter.export(roots, gltfPath);
						} catch (IOException e) {
							Logger.error(e, "Failed to export {}", gltfPath);
							return 1;
						}
					}
				}
			}
			case PARSE -> { /* already printed summary above */ }
		}
		return errors.isEmpty() ? 0 : 1;
	}
}