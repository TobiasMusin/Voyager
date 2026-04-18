package io.github.tomusin.lodDataRecords;

import org.tinylog.Logger;

import io.github.tomusin.voyager.utils.BitByteBuffer;

/**
 * Int32 Probability Context Table Entry
 * 
 * Represents a single entry in an Int32 Probability Context Table.
 * Used by the Arithmetic codec for entropy decoding.
 * 
 * Structure (JT Specification):
 * - U32{1}: Is Escape Symbol - boolean flag
 * - U32{numberOccurrenceCountBits}: Occurrence Count - relative frequency of the value
 * - I32{numberValueBits}: Associated Value - the actual value this symbol represents (stored with Min Value subtracted)
 */
public record Int32ProbabilityContextTableEntryRecord(
		boolean isEscapeSymbol,
		int occurrenceCount,
		int associatedValue,
		int totalBitsRead  // Total bits consumed by this entry
) {
	
	/**
	 * Deserialize an Int32 Probability Context Table Entry from a bit buffer.
	 * Note: This is called internally by the parent context record.
	 * Reference: JT Specification - Int32 Probability Context Table Entry
	 * 
	 * @param buffer BitByteBuffer to read from
	 * @param startBitIndex Bit index to start reading from
	 * @param numberOccurrenceCountBits Number of bits used to encode occurrence count
	 * @param numberValueBits Number of bits used to encode values
	 * @return Deserialized entry
	 */
	public static Int32ProbabilityContextTableEntryRecord fromBitBuffer(
			BitByteBuffer buffer,
			int startBitIndex,
			int numberOccurrenceCountBits, 
			int numberValueBits) {
		
		int currentBitIndex = startBitIndex;
		
		// Read: U32{1} - Is Escape Symbol
		boolean isEscapeSymbol = buffer.readBitsAt(currentBitIndex, 1) != 0;
		currentBitIndex += 1;
		Logger.debug("    Entry: isEscapeSymbol={}", isEscapeSymbol);
		
		// Read: U32{numberOccurrenceCountBits} - Occurrence Count
		int occurrenceCount = buffer.readBitsAt(currentBitIndex, numberOccurrenceCountBits);
		currentBitIndex += numberOccurrenceCountBits;
		Logger.debug("    Entry: occurrenceCount={}", occurrenceCount);
		
		// Read: I32{numberValueBits} - Associated Value (stored with Min Value subtracted)
		// Note: Will be added back to minValue when used
		int associatedValue = buffer.readBitsAt(currentBitIndex, numberValueBits);
		currentBitIndex += numberValueBits;
		Logger.debug("    Entry: associatedValue={}", associatedValue);
		
		// Calculate total bits for this entry: 1 + occurrenceCountBits + valueBits
		int totalBits = 1 + numberOccurrenceCountBits + numberValueBits;
		
		return new Int32ProbabilityContextTableEntryRecord(
				isEscapeSymbol, 
				occurrenceCount, 
				associatedValue,
				totalBits);
	}
	
	/**
	 * Get the total bits read for this entry (used internally for offset tracking).
	 * 
	 * @return Number of bits consumed by this entry
	 */
	public int getTotalBitsRead() {
		return totalBitsRead;
	}
	
	/**
	 * Check if this is an escape symbol entry.
	 * At most one entry will have this flag set to true in any context.
	 * 
	 * @return true if this entry represents an escape symbol
	 */
	public boolean isEscapeSymbol() {
		return isEscapeSymbol;
	}
	
	@Override
	public String toString() {
		return String.format(
				"Int32ProbabilityContextTableEntryRecord[isEscapeSymbol=%s, occurrenceCount=%d, associatedValue=%d]",
				isEscapeSymbol, occurrenceCount, associatedValue);
	}
}
