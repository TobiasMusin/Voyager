# Export Validation Plan

## Purpose

This document defines the repeatable loop for turning JT examples into verified glTF mesh exports:

1. export a known JT fixture;
2. validate the produced glTF structure and binary data;
3. inspect mesh topology and bounds;
4. fix the smallest reader or exporter defect revealed by the check;
5. rerun the same fixture and then expand to the broader corpus.

The target is a valid glTF 2.0 triangle mesh, not merely a point cloud. A successful process exit is not acceptance by itself.

## Current Baseline

Date checked: 2026-09-13.

The project has a `.gltf` exporter, not a `.glb` exporter. `GltfExporter` writes JSON glTF 2.0 with one base64-embedded binary buffer. It currently emits one `POINTS` primitive (`mode: 0`) per geometry-bearing tree node and has no index accessor. It cannot produce a renderable surface mesh until the JT face/topology data is decoded and emitted as `TRIANGLES` (`mode: 4`).

Two initial fixtures were exercised:

```powershell
mvn exec:java -DskipTests "-Dexec.args=--parse src/main/resources/example_block_jt10.3.jt"
mvn exec:java -DskipTests "-Dexec.args=--export --output target/validation/example_block.gltf src/main/resources/example_block_jt10.3.jt"

mvn exec:java -DskipTests "-Dexec.args=--export --output target/validation/nist_ftc_09.gltf src/main/resources/10.6/nist_ftc_09_asme1_ap242_e1.jt"
```

Both files initially parsed an LSG hierarchy but reported `No geometry nodes found ? nothing to export.` The source of that regression was verified from the JT bytes and repaired: a ShapeLOD data segment begins immediately after its 24-byte segment header, with the first four payload bytes holding the logical-element length. The current reader had incorrectly treated those bytes as a compression block and started at offset 28, four bytes into the first element GUID.

The ShapeLOD decode path is now aligned through the topologically compressed vertex records. The primary fixture's legacy embedded topology header is recognized, vertex normal hashes are read immediately after their CDPs, and vertex flag arrays consume their documented leading count. `Int32CDPOffsetTest` confirms the fixture parses without CDP validation errors.

ShapeLOD linkage now resolves `JT_LLPROP_SHAPEIMPL` late-loaded segment references rather than attaching the payload object ID `0` to the partition root. The primary fixture now exports one nonempty, structurally valid point primitive from its actual Tri-Strip Shape Node, with 8 decoded positions. This is a diagnostic milestone only: it is not yet an acceptable mesh because the exporter still writes `POINTS` and topology reconstruction is not implemented.

The complete bundled-fixture scan also found no working export. All 42 JT files under `src/main/resources/10.6`, including its four nested files, generated zero `.gltf` files. The 11 root-level `example_block` copies are byte-identical and each reported no geometry. `src/main/resources/9.5/CoffeeMaker.jt` fails earlier with unsupported segment type 28. The duplicated `CAD-Files/JT` tree was not scanned because it mirrors the 10.6 corpus.

This is not yet evidence that glTF serialization is invalid; no generated glTF exists to inspect. It is evidence that export success must require both a generated file and at least one geometry primitive.

## Inputs

Primary fixture:

```text
src/main/resources/example_block_jt10.3.jt
```

The small 10.6 assembly fixture is:

```text
src/main/resources/10.6/nist_ftc_09_asme1_ap242_e1.jt
```

The repository includes 96 JT files. `src/main/resources/10.6` contains 42 of them and is the broad, non-duplicated corpus. `src/main/resources/CAD-Files/JT` duplicates much of it, so it should not be used in the initial regression loop.

## Run Commands

Compile and run tests first:

```powershell
mvn clean test
```

Inspect a fixture with parser diagnostics:

```powershell
mvn exec:java -DskipTests "-Dexec.args=--parse --log-level INFO src/main/resources/example_block_jt10.3.jt"
```

Run the automated export-and-validation wrapper:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/Export-And-Validate.ps1 `
  -InputPath src/main/resources/example_block_jt10.3.jt `
  -OutputPath target/validation/example_block.gltf
```

After topology export is implemented, use the required mesh gate:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/Export-And-Validate.ps1 `
  -InputPath src/main/resources/example_block_jt10.3.jt `
  -OutputPath target/validation/example_block.gltf `
  -RequireTriangles
```

`target/validation` is ignored by Git and is the intended location for generated inspection artifacts.

## What Correct Means

The validator checks these machine-verifiable invariants for `.gltf` files:

- valid JSON and `asset.version` equal to `2.0`;
- a base64 data-URI buffer whose decoded length matches `buffers[0].byteLength`;
- every position accessor has valid buffer-view bounds;
- every `POSITION` accessor is `FLOAT`, `VEC3`, with a byte length of `count * 12`;
- position bytes decode to finite IEEE-754 values;
- accessor `min` and `max` match the decoded values within floating-point tolerance;
- at least one mesh primitive exists and every primitive has a `POSITION` attribute;
- when `-RequireTriangles` is used, primitives use mode 4 and contain indices whose count is divisible by 3.

For the present point-cloud exporter, a structurally valid `.gltf` could pass without `-RequireTriangles`, but it is only a diagnostic milestone. The final mesh acceptance gate always uses `-RequireTriangles`.

Visual inspection remains useful after the structural gate passes. Open the `.gltf` in a glTF-capable viewer and confirm that the model has plausible scale, orientation, extents, and closed-looking surfaces. Compare vertex and triangle counts with JT metadata such as `_nTrisLODs` where available. A viewer cannot prove index correctness, so it complements rather than replaces the script.

## Face-Decoding Work

`TopologicallyCompressedRepDataRecord` already retains face-oriented streams, including `faceDegrees`, `vertexValences`, `vertexGroups`, `vertexFlags`, `splitFaceSyms`, and `splitFacePositions`. The missing implementation is the topology reconstruction that converts those compressed streams into a validated index list.

The implementation sequence should be:

1. expose a mesh-data object containing dequantized positions and decoded indices from `TreeNode`;
2. decode a single topology fixture into faces, preserving face boundaries and winding;
3. reject malformed faces: fewer than three vertices, indices outside `[0, vertexCount)`, duplicate-only faces, or non-finite positions;
4. triangulate polygons deterministically and emit a glTF `indices` accessor with `UNSIGNED_SHORT` or `UNSIGNED_INT` as required;
5. change the primitive mode from `POINTS` to `TRIANGLES`;
6. add a regression test that checks nonzero triangle output, index bounds, and triangle count;
7. run this script with `-RequireTriangles` for the primary fixture before expanding to all 10.6 files.

Do not fabricate faces from sequential vertices or from point-cloud proximity. Such output can look plausible but is geometrically incorrect. Faces must come from the JT topological streams.

## Exit Criteria

The first acceptable mesh result has all of the following:

- export creates a nonempty `.gltf` file;
- the structural validator passes with `-RequireTriangles`;
- every index is in range and the index count is a multiple of three;
- the decoded positions and bounds are finite and self-consistent;
- a glTF viewer displays surfaces rather than isolated points;
- a focused automated regression test passes for the fixture;
- `mvn clean test` passes.

GLB can be added later as a packaging step. It is not currently implemented and should not be claimed as an output format until the exporter writes a GLB header and JSON/BIN chunks or uses a tested glTF library to do so.