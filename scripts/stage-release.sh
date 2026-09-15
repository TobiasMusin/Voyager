#!/usr/bin/env bash
set -euo pipefail

platform="$1"
version="$2"
artifact_name="voyager-${version}-${platform}-x64"
converter_name="${artifact_name}-converter"
stage_directory="target/${artifact_name}"
release_directory="release"

rm -rf "$stage_directory"
mkdir -p "$stage_directory" "$release_directory"
cp target/Voyager "$stage_directory/voyager"
cp README.md LICENCE "$stage_directory/"
zip -q -j "${release_directory}/${converter_name}.zip" target/Voyager

for module in lwjgl lwjgl-glfw lwjgl-opengl; do
  native_jar=$(find "$HOME/.m2/repository/org/lwjgl/$module" -name "${module}-*-natives-${platform}.jar" | sort | tail -n 1)
  test -n "$native_jar"
  unzip -q -o "$native_jar" -d "$stage_directory"
done
find "$stage_directory" -type f -name '*.so' -exec mv -f {} "$stage_directory" \;
find "$stage_directory" -mindepth 1 -type d -exec rm -rf {} +
chmod +x "$stage_directory/voyager"
(
  cd "$stage_directory"
  zip -q -r "../../${release_directory}/${artifact_name}.zip" .
)