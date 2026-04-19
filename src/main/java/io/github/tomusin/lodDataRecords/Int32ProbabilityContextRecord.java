package io.github.tomusin.lodDataRecords;

import java.util.ArrayList;
import java.util.List;

import org.tinylog.Logger;

import io.github.tomusin.voyager.utils.BitByteBuffer;

/**
 * Int32 Probability Context Table
 * 
 * Used by the Arithmetic codec to decode symbols during entropy decoding.
 * Contains the probability context table entries and metadata.
 * 
 * Reference: JT Specification - Int32 Probability Context
 * 
 * Structure:
 * - U32{16}: Probability Context Table Entry Count - number of entries
 * - U32{6}: Number Occurrence Count Bits - bits used to encode occurrence counts
 * - U32{7}: Number Value Bits - bits used to encode values (only in first table)
 * - U32{32}: Min Value - minimum value (added back to stored values)
 * - U32{variable}: Alignment Bits - padding to next byte boundary
 * - Int32ProbabilityContextTableEntry[count]: The actual entries
 */
public record Int32ProbabilityContextRecord(
		int entryCount,
		int numberOccurrenceCountBits,
		int numberValueBits,
		int minValue,
		List<Int32ProbabilityContextTableEntryRecord> entries,
		int jtEndBitIndex,  // Bit index after alignment to next byte
		int totalBitsRead   // Total bits read from the buffer
) {
	
	/**
	 * Deserialize an Int32 Probability Context from a bit buffer.
	 * Reads all header fields, entries, and handles byte alignment.
	 * Reference: JT Specification - Int32 Probability Context
	 * 
	 * @param buffer BitByteBuffer to read from
	 * @param startBitIndex Bit offset to start reading from
	 * @param valueBitsFromFirstContext Value bits from first context (use if not first context)
	 * @return Deserialized probability context with end bit index
	 */
	public static Int32ProbabilityContextRecord fromBitBuffer(
			BitByteBuffer buffer,
			int startBitIndex,
			Integer valueBitsFromFirstContext) {
		
		Logger.info("Int32ProbabilityContextRecord.fromBitBuffer: startBitIndex={}", startBitIndex);
		
		int currentBitIndex = startBitIndex;
		
		// ========== STEP 1: Read Header Fields (JT Specification) ==========
		
		// Read: U32{16} - Probability Context Table Entry Count
		int entryCount = buffer.readBitsAt(currentBitIndex, 16);
		currentBitIndex += 16;
		Logger.info("  Entry Count: {}", entryCount);
		
		// Read: U32{6} - Number Occurrence Count Bits
		int numberOccurrenceCountBits = buffer.readBitsAt(currentBitIndex, 6);
		currentBitIndex += 6;
		Logger.info("  Number Occurrence Count Bits: {}", numberOccurrenceCountBits);
		
		// Read: U32{7} - Number Value Bits
		// Note: Only read if this is the first context table.
		// If not first, use the valueBits from the first context.
		int numberValueBits;
		if (valueBitsFromFirstContext != null) {
			// This is not the first context table - skip reading valueBits
			numberValueBits = valueBitsFromFirstContext;
			Logger.info("  Number Value Bits (from first context): {}", numberValueBits);
		} else {
			// This is the first context table - read valueBits
			numberValueBits = buffer.readUnsignedBitsAt(currentBitIndex, 7);
			currentBitIndex += 7;
			Logger.info("  Number Value Bits: {}", numberValueBits);
		}
		
		// Read: U32{32} - Min Value
		int minValue = buffer.readBitsAt(currentBitIndex, 32);
		currentBitIndex += 32;
		Logger.info("  Min Value: {}", minValue);
		
		Int32ProbabilityContextRecord result;
		if ( numberOccurrenceCountBits < 0 || numberOccurrenceCountBits > 32
				|| numberValueBits < 0 || numberValueBits > 32) {
			Logger.warn("  WARNING: numberOccurrenceCountBits {} is out of expected range [0, 32]", numberOccurrenceCountBits);
			result = new Int32ProbabilityContextRecord(
					entryCount,
					numberOccurrenceCountBits,
					numberValueBits,
					minValue,
					null,
					currentBitIndex,
					currentBitIndex - startBitIndex);
		} else {
		Logger.debug("  Header fields read successfully, proceeding to read entries...");
		// ========== STEP 2: Read Entries ==========
		List<Int32ProbabilityContextTableEntryRecord> entries = new ArrayList<>();
		
		for (int i = 0; i < entryCount; i++) {
				Int32ProbabilityContextTableEntryRecord entry = Int32ProbabilityContextTableEntryRecord.fromBitBuffer(buffer, currentBitIndex, numberOccurrenceCountBits, numberValueBits);
				entries.add(entry);
				currentBitIndex += entry.getTotalBitsRead();
				Logger.debug("  Entry {}: {}", i, entry);
			}
			
		// ========== STEP 3: Handle Alignment Bits ==========
		int alignmentBits = (8 - (currentBitIndex % 8)) % 8;
		if (alignmentBits > 0) {
			Logger.debug("  Alignment bits to skip: {}", alignmentBits);
			currentBitIndex += alignmentBits;
		}
		
		Logger.info("  End bit index (after alignment): {} (read {} bits)", 
				currentBitIndex, currentBitIndex - startBitIndex);
			
			result = new Int32ProbabilityContextRecord(
					entryCount,
					numberOccurrenceCountBits,
					numberValueBits,
					minValue,
					entries,
					currentBitIndex,
					currentBitIndex - startBitIndex);
		}
		
		
		// ========== STEP 4: Validate the record ==========
		int bufferCapacityBits = buffer.capacity() * 8;
		if (result.isValid(bufferCapacityBits)) {
			Logger.debug("  VALID: Int32ProbabilityContextRecord is valid");
		} else {
			Logger.warn("  Int32ProbabilityContextRecord is INVALID, attempting to find valid offset...");
			Integer offsetDiff = scanForValidOffset(buffer, startBitIndex, 512);
			if (offsetDiff != Integer.MAX_VALUE) {
				Logger.warn("  Found possibly valid offset at {} bits from current position", offsetDiff);
			}
		}
		
		return result;
	}
	
	/**
	 * Deserialize the first probability context (which includes valueBits field).
	 * 
	 * @param buffer BitByteBuffer to read from
	 * @param startBitIndex Bit offset to start reading from
	 * @return Deserialized probability context with end bit index
	 */
	public static Int32ProbabilityContextRecord fromBitBuffer(
			BitByteBuffer buffer,
			int startBitIndex) {
		return fromBitBuffer(buffer, startBitIndex, null);
	}
	
	/**
	 * Get the end bit index after reading this probability context (including alignment).
	 * 
	 * @return Bit index pointing to the start of the next structure
	 */
	public int jtEndIndex() {
		return jtEndBitIndex;  // Now stores bit index
	}
	
	/**
	 * Get the total number of bits read from the buffer.
	 * 
	 * @return Total bits consumed from the buffer
	 */
	public int getBitsRead() {
		return totalBitsRead;
	}
	
	/**
	 * Get the total number of bytes read from the buffer (rounded up).
	 * 
	 * @return Total bytes consumed from the buffer
	 */
	public int getBytesRead() {
		return (totalBitsRead + 7) / 8;
	}
	
	/**
	 * Validate if this Int32ProbabilityContextRecord is valid.
	 * Checks:
	 * - entryCount: 0 <= entryCount < 10000
	 * - numberOccurrenceCountBits: 0 <= bits <= 32
	 * - numberValueBits: 0 <= bits <= 32
	 * - minValue: must be positive (> 0)
	 * - totalBitsRead: must be < buffer capacity (passed as parameter)
	 * 
	 * @param bufferCapacityBits Buffer capacity in bits for validation
	 * @return true if record is valid, false otherwise
	 */
	public boolean isValid(int bufferCapacityBits) {
		if (entryCount < 0 || entryCount >= 10000) {
			Logger.warn("  INVALID: entryCount {} out of range [0, 10000)", entryCount);
			return false;
		}
		if (numberOccurrenceCountBits < 0 || numberOccurrenceCountBits > 32) {
			Logger.warn("  INVALID: numberOccurrenceCountBits {} out of range [0, 32]", numberOccurrenceCountBits);
			return false;
		}
		if (numberValueBits < 0 || numberValueBits > 32) {
			Logger.warn("  INVALID: numberValueBits {} out of range [0, 32]", numberValueBits);
			return false;
		}
		// minValue can be any I32 including 0 and negative values
		if (totalBitsRead >= bufferCapacityBits) {
			Logger.warn("  INVALID: totalBitsRead {} >= buffer capacity {}", totalBitsRead, bufferCapacityBits);
			return false;
		}
		return true;
	}
	
	/**
	 * Scan for a valid Int32ProbabilityContextRecord starting from a different offset.
	 * Scans both backward and forward from the current start offset by reading and validating header fields only.
	 * 
	 * @param buffer BitByteBuffer to read from
	 * @param startBitIndex Current bit offset that was found invalid
	 * @param maxScanBits Maximum bits to scan in each direction
	 * @return Offset difference in bits if found, or Integer.MAX_VALUE if not found
	 */
	public static Integer scanForValidOffset(BitByteBuffer buffer, int startBitIndex, int maxScanBits) {
		Logger.info("Scanning for valid Int32ProbabilityContext offset from bit {}, max scan {} bits", startBitIndex, maxScanBits);
		
		int bufferCapacityBits = buffer.capacity() * 8;
		
		// Scan forward
		for (int offset = 0; offset <= maxScanBits; offset += 8) {
			int testBitIndex = startBitIndex + offset;
			if (testBitIndex + 100 > bufferCapacityBits) break;  // Ensure enough space for header
			
			try {
				// Read header fields directly without calling fromBitBuffer to avoid recursion
				int currentBitIndex = testBitIndex;
				
				// Read: U32{16} - Probability Context Table Entry Count
				int entryCount = buffer.readBitsAt(currentBitIndex, 16);
				currentBitIndex += 16;
				
				// Read: U32{6} - Number Occurrence Count Bits
				int numberOccurrenceCountBits = buffer.readBitsAt(currentBitIndex, 6);
				currentBitIndex += 6;
				
				// Read: U32{7} - Number Value Bits
				int numberValueBits = buffer.readUnsignedBitsAt(currentBitIndex, 7);
				currentBitIndex += 7;
				
				// Read: U32{32} - Min Value
				int minValue = buffer.readBitsAt(currentBitIndex, 32);
				
				// Validate header fields
				if (entryCount >= 0 && entryCount < 10000
						&& numberOccurrenceCountBits >= 0 && numberOccurrenceCountBits <= 32
						&& numberValueBits >= 0 && numberValueBits <= 32
						&& minValue > 0) {
					Logger.info("  FOUND VALID offset at +{} bits (bit index {})", offset, testBitIndex);
					return offset;
				}
			} catch (Exception e) {
				// Continue scanning
			}
		}
		
		// Scan backward
		for (int offset = 8; offset <= maxScanBits; offset += 8) {
			int testBitIndex = startBitIndex - offset;
			if (testBitIndex < 0) break;
			
			try {
				// Read header fields directly without calling fromBitBuffer to avoid recursion
				int currentBitIndex = testBitIndex;
				
				// Read: U32{16} - Probability Context Table Entry Count
				int entryCount = buffer.readBitsAt(currentBitIndex, 16);
				currentBitIndex += 16;
				
				// Read: U32{6} - Number Occurrence Count Bits
				int numberOccurrenceCountBits = buffer.readBitsAt(currentBitIndex, 6);
				currentBitIndex += 6;
				
				// Read: U32{7} - Number Value Bits
				int numberValueBits = buffer.readUnsignedBitsAt(currentBitIndex, 7);
				currentBitIndex += 7;
				
				// Read: U32{32} - Min Value
				int minValue = buffer.readBitsAt(currentBitIndex, 32);
				
				// Validate header fields
				if (entryCount >= 0 && entryCount < 10000
						&& numberOccurrenceCountBits >= 0 && numberOccurrenceCountBits <= 32
						&& numberValueBits >= 0 && numberValueBits <= 32
						&& minValue > 0) {
					Logger.info("  FOUND VALID offset at -{} bits (bit index {})", offset, testBitIndex);
					return -offset;
				}
			} catch (Exception e) {
				// Continue scanning
			}
		}
		
		Logger.warn("  No valid offset found within {} bits", maxScanBits);
		return Integer.MAX_VALUE;
	}
	
	/**
	 * Find an entry by its symbol (index in the entry list).
	 * 
	 * @param symbolIndex Index of the symbol in the probability context
	 * @return The entry, or null if index is out of range
	 */
	public Int32ProbabilityContextTableEntryRecord getEntry(int symbolIndex) {
		if (symbolIndex < 0 || symbolIndex >= entries.size()) {
			return null;
		}
		return entries.get(symbolIndex);
	}
	
	/**
	 * Find the escape symbol entry if present.
	 * 
	 * @return The escape symbol entry, or null if none exists
	 */
	public Int32ProbabilityContextTableEntryRecord getEscapeSymbolEntry() {
		for (Int32ProbabilityContextTableEntryRecord entry : entries) {
			if (entry.isEscapeSymbol()) {
				return entry;
			}
		}
		return null;
	}
	
	/**
	 * Calculate the total count (sum of all occurrence counts).
	 * Used during arithmetic decoding.
	 * 
	 * @return Sum of all occurrence counts
	 */
	public int calculateTotalCount() {
		int total = 0;
		for (Int32ProbabilityContextTableEntryRecord entry : entries) {
			total += entry.occurrenceCount();
		}
		return total;
	}
	
	/**
	 * Get an actual value from an entry, adding back the min value.
	 * (Entries store values with minValue subtracted for compression)
	 * 
	 * @param entry The entry to get the value from
	 * @return The actual associated value
	 */
	public int getActualValue(Int32ProbabilityContextTableEntryRecord entry) {
		return entry.associatedValue() + minValue;
	}
	
	@Override
	public String toString() {
		return String.format(
				"Int32ProbabilityContextRecord[entryCount=%d, occCountBits=%d, valueBits=%d, minValue=%d, entries=%d]",
				entryCount, numberOccurrenceCountBits, numberValueBits, minValue, entries.size());
	}
}
