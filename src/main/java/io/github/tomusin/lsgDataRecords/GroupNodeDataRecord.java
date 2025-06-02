package io.github.tomusin.lsgDataRecords;

import java.util.Set;

public record GroupNodeDataRecord(BaseNodeDataRecord baseNodeDataRecord, 
		int versionNumber, 
		int childCount, Set<Integer> childNodeObjectIDSet, 
		int jtStartIndex,
	    int jtEndIndex) {
}
