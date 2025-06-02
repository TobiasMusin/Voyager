package io.github.tomusin.voyager.datastructures;

public record LogicalElementHeaderRecord(
	    int elementLength,
	    String objectTypeID,
	    int objectBaseType,
	    int objectID,
	    int jtEndIndex
	) {}

