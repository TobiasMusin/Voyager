# voyager

voyager is a work-in-progress jt reader for jt version 10 and related files.

it can parse the scene graph, link some geometry to tree nodes, reconstruct selected topology, and export triangle meshes to gltf 2.0.

this project was mainly built as a learning and experimentation exercise, so it is not intended to be treated as production software.

this repository is being published as an honest snapshot of the current state. it is not a complete jt implementation.

## what works

- reads jt files and builds an internal scene graph
- parses node types, attributes, and some metadata-related structures
- links available shape lod geometry to tree nodes through late-loaded shape references
- reconstructs indexed triangle topology for supported topologically compressed ShapeLOD data
- exports indexed triangle meshes to a `.gltf` file
- writes gltf with an embedded base64 buffer, so no separate `.bin` file is needed
- provides a simple opengl viewer with a retained JT scene graph, per-node visibility, filled faces, triangle edges, and vertices for decoded meshes
- supports parsing one file, multiple files, or a directory of `.jt` files
- supports `parse`, `render`, and `export` modes

## what does not work yet

- topology decoding is currently verified on selected JT 10.x fixtures and needs broader format coverage
- normals, uvs, and material-aware mesh export are not implemented
- the exported gltf does not preserve the jt node tree hierarchy
- metadata nodes and the node tree are parsed internally, but they are not exported to gltf
- the viewer is experimental and may render incorrectly or incompletely
- some parts of the jt format still depend on interpretation and best-effort assumptions because the official specification is incomplete or inconsistent in places
- there are version-specific workarounds in the reader, especially around segment offsets and jt writer quirks

## requirements

- jdk 25
- apache maven 3.9 or newer
- parsing and export work on any platform with jdk 25 and maven; the lwjgl viewer downloads matching windows, linux, or macos x64 and arm64 natives automatically
- for native image builds: a Java 25-compatible GraalVM and platform-native toolchain are required

## build

make sure `java` and `mvn` are available on your path.

```cmd
java -version
mvn -version
```

if you need to point the build to a specific jdk, set `JAVA_HOME` first:

```cmd
set "JAVA_HOME=C:\path\to\jdk-25"
set "PATH=%JAVA_HOME%\bin;%PATH%"
```

On PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

run the tests:

```cmd
mvn clean test
```

build the project:

```cmd
mvn clean package
```

## releases

Each platform release contains a converter archive with one native executable for `parse` and `export`, plus a viewer archive that includes the LWJGL native libraries required by `render`.

## run

parse a file:

```cmd
mvn exec:java -Dexec.args="--parse C:\path\to\file.jt"
```

render a file:

```cmd
mvn exec:java -Dexec.args="--render C:\path\to\file.jt"
```

On Linux or macOS, use POSIX paths in the same command.

The viewer starts with a grey Blinn-Phong material and directional light. Rendered shapes retain their JT node identity, so selection and visibility apply to individual nodes rather than the full model.

The viewer also opens a **JT Scene Graph** control window. Select a node in the expandable tree to highlight its geometry; use **Toggle Visibility** to hide/show a node and its rendered descendants. Hover a node to inspect its parsed metadata and attributes for debugging.

Viewer controls:

- `C`: toggle coordinate-based colors
- `F`: toggle filled faces
- `E`: toggle triangle edges
- `V`: toggle vertices
- `R`: reset the camera
- Up/Down: select the previous/next scene node in tree order
- Space: toggle visibility of the selected node and its rendered descendants
- Escape: close the viewer

export one file to a specific gltf path:

```cmd
mvn exec:java -Dexec.args="--export --output C:\path\to\output.gltf C:\path\to\file.jt"
```

export multiple files from a directory:

```cmd
mvn exec:java -Dexec.args="--export --output C:\path\to\output-folder C:\path\to\jt-folder"
```

## command line options

default mode is parse.

- `-p`, `--parse`  
  parse and print a geometry summary only

- `-r`, `--render`  
  parse and open the 3d viewer

- `-e`, `--export`  
  parse and export to gltf

- `-h`, `--help`  
  show help

- `-v`, `--verbose`  
  enable info-level logging

- `--log-level LEVEL`  
  set the log level, for example `debug`, `info`, `warn`, or `error`

- `-o`, `--output PATH`  
  output file or directory for export

- `--parallel`  
  read multiple files in parallel

## limitations

the reader and exporter are intentionally incomplete.

- only positions and reconstructed triangle indices are exported to gltf
- topology decoding is incomplete for the broader JT corpus
- the gltf exporter currently writes a flat set of nodes for geometry-bearing objects
- the jt hierarchy is available internally, but it is not preserved in the exported gltf
- some jt files may still fail because the format documentation is incomplete or ambiguous
- some values are inferred from observed file behavior rather than from a perfectly reliable spec

## project status

this repository is useful for:

- inspecting some jt scene graphs
- extracting vertex data
- experimenting with jt structure
- trying out a partial gltf export

it is not yet suitable as a fully reliable jt converter.

## license

see the included license file in the repository root.

## notes for contributors

contributions are welcome, but this repository is currently published as a snapshot and there is no guarantee that suggestions or pull requests will be acted on.

before publishing or extending this repository further, consider:

- removing personal file paths from tests and examples
- keeping generated output out of version control
- reviewing debug or benchmark scripts you do not want to publish
- replacing guesses and format workarounds with documented behavior where possible

## acknowledgements

this project relies on a number of assumptions about the jt file format, because the available specification is incomplete in places and some writer behavior differs from the documented format.