package io.github.tomusin.voyager.fileRecords;

public record LogicalElementHeaderCompressed(long compressionFlag, int compressedDataLength, int compressionAlgorithm) {

}
