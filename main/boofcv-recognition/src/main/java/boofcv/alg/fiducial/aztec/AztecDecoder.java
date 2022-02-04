/*
 * Copyright (c) 2022, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.fiducial.aztec;

import boofcv.alg.fiducial.aztec.AztecCode.Encodings;
import boofcv.alg.fiducial.qrcode.PackedBits8;
import boofcv.alg.fiducial.qrcode.ReedSolomonCodes_U16;
import boofcv.misc.BoofMiscOps;
import org.ddogleg.struct.DogArray_I16;
import org.ddogleg.struct.VerbosePrint;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Objects;
import java.util.Set;

/**
 * Converts a raw binary stream read from the image and converts it into a String message.
 *
 * @author Peter Abeles
 */
public class AztecDecoder implements VerbosePrint {
	// generates ECC for different Galois Fields. Which one is used depends on how large the marker is
	ReedSolomonCodes_U16 ecc6 = new ReedSolomonCodes_U16(6, 67);
	ReedSolomonCodes_U16 ecc8 = new ReedSolomonCodes_U16(8, 301);
	ReedSolomonCodes_U16 ecc10 = new ReedSolomonCodes_U16(10, 1033);
	ReedSolomonCodes_U16 ecc12 = new ReedSolomonCodes_U16(12, 4201);

	// The data portion of the message converted into a format ECC generation can understand
	DogArray_I16 storageDataWords = new DogArray_I16();
	DogArray_I16 storageEccWords = new DogArray_I16();

	//------------------ state of decoder -----------------
	// What type of characters are encoded
	Encodings current = Encodings.UPPER;
	// If not latched (shift mode) then it will switch back to this encoding after 1 character
	@Nullable Encodings shiftEncoding = null;
	// if lacked it won't revert to the old encoding
	boolean latched;
	// Results of decoded
	StringBuilder workString = new StringBuilder();

	@Nullable PrintStream verbose = null;

	/**
	 * Extracts the message from this marker.
	 *
	 * @param marker Marker which is to be decoded
	 * @return true if successful
	 */
	public boolean process( AztecCode marker ) {
		// Sanity check that the marker has been set up correctly
		BoofMiscOps.checkTrue(marker.dataLayers >= 1);
		Objects.requireNonNull(marker.rawbits);

		// Apply error correction to the message
		boolean success = switch (marker.getWordBitCount()) {
			case 6 -> applyEcc(marker, ecc6);
			case 8 -> applyEcc(marker, ecc8);
			case 10 -> applyEcc(marker, ecc10);
			case 12 -> applyEcc(marker, ecc12);
			default -> throw new RuntimeException("Unexpected word size");
		};
		if (!success)
			return false;

		// Remove padding from the encoded bits
		PackedBits8 paddedBits = PackedBits8.wrap(marker.corrected, marker.messageWordCount*marker.getWordBitCount());
		PackedBits8 bits = removeExtraBits(marker.getWordBitCount(), paddedBits);
		return bitsToMessage(marker, bits);
	}

	/**
	 * Applies error correction to data portion of rawbits, then copies the results into marker.corrected.
	 *
	 * @return true if nothing went wrong with error correction
	 */
	boolean applyEcc( AztecCode marker, ReedSolomonCodes_U16 ecc ) {
		int wordBitCount = marker.getWordBitCount();
		PackedBits8 bits = PackedBits8.wrap(marker.rawbits, marker.getCapacityBits());

		// convert the rawbits into a format ECC can understand
		storageDataWords.resize(marker.messageWordCount);
		storageEccWords.resize(marker.getCapacityWords() - marker.messageWordCount);

		int locationBits = 0;
		for (int i = 0; i < storageDataWords.size; i++, locationBits += wordBitCount) {
			storageDataWords.data[i] = (short)bits.read(locationBits, wordBitCount, true);
		}
		for (int i = 0; i < storageEccWords.size; i++, locationBits += wordBitCount) {
			storageEccWords.data[i] = (short)bits.read(locationBits, wordBitCount, true);
		}

		// TODO check for words with all 0 and all 1 and mark word as a known erasure

		// Apply error correction
		ecc.generatorAztec(marker.getCapacityWords() - storageDataWords.size);
		if (!ecc.correct(storageDataWords, storageEccWords)) {
			if (verbose != null) verbose.println("ECC failed");
			return false;
		}
		marker.totalBitErrors = ecc.getTotalErrors();

		// Save the corrected data

		int messageBits = storageDataWords.size*wordBitCount;
		marker.corrected = new byte[BoofMiscOps.bitToByteCount(messageBits)];
		bits.size = 0;
		bits.data = marker.corrected;
		for (int i = 0; i < storageDataWords.size; i++) {
			int value = storageDataWords.get(i) & 0xFFFF;

			// TODO handle special case words for all zeros and ones

			bits.append(value, wordBitCount, false);
		}

		return true;
	}

	/**
	 * Removes extra bits in raw stream caused by padding being added to avoid a word that's all zero or ones.
	 */
	static PackedBits8 removeExtraBits( int wordBitCount, PackedBits8 bitsExtras ) {
		// sanity check to make sure only whole words are inside the input bit array
		BoofMiscOps.checkTrue(bitsExtras.size%wordBitCount == 0);

		int numWords = bitsExtras.size/wordBitCount;

		// Remove extra 0 and 1 added to data stream
		int onesMinusOne = (1 << wordBitCount) - 2;
		var bits = new PackedBits8();
		for (int i = 0; i < numWords; i++) {
			int value = bitsExtras.read(i*wordBitCount, wordBitCount, true);
			if (value == 1 || value == onesMinusOne) {
				bits.append(value >> 1, wordBitCount - 1, false);
			} else {
				bits.append(value, wordBitCount, false);
			}
		}
		return bits;
	}

	/**
	 * Converts the corrected bits into a message.
	 *
	 * @return true if successful
	 */
	boolean bitsToMessage( AztecCode marker, PackedBits8 bits  ) {
		// Reset the state
		latched = false;
		shiftEncoding = null;
		workString.delete(0, workString.length());
		current = Encodings.UPPER;

		int location = 0;
		while (location + current.wordSize <= bits.size) {
			int value = bits.read(location, current.wordSize, true);
			if (verbose != null) verbose.println("current=" + current + " latched=" + latched + " value=" + value);

			location += current.wordSize;
			latched = true;
			Encodings previous = current;
			boolean success = switch (current) {
				case UPPER -> handleUpper(value);
				case LOWER -> handleLower(value);
				case MIXED -> handleMixed(value);
				case PUNCT -> handlePunct(value);
				case DIGIT -> handleDigit(value);
				default -> {
					if (verbose != null) verbose.println("Unhandled encoding: " + current);
					yield false;
				}
			};
			if (!success)
				return false;

			if (shiftEncoding != null) {
				current = shiftEncoding;
			}
			shiftEncoding = latched ? null : previous;
		}

		marker.message = workString.toString();

		return true;
	}

	boolean handleUpper( int value ) {
		if (value == 0) {
			current = Encodings.PUNCT;
			latched = false;
		} else if (value == 1) {
			workString.append(' ');
		} else if (value <= 27) {
			workString.append((char)('A' + (value - 2)));
		} else {
			switch (value) {
				case 28 -> current = Encodings.LOWER;
				case 29 -> current = Encodings.MIXED;
				case 30 -> current = Encodings.DIGIT;
				case 31 -> {current = Encodings.BYTE; latched = false;}
			}
		}
		return true;
	}

	boolean handleLower( int value ) {
		if (value == 0) {
			current = Encodings.PUNCT;
			latched = false;
		} else if (value == 1) {
			workString.append(' ');
		} else if (value <= 27) {
			workString.append((char)('a' + (value - 2)));
		} else {
			switch (value) {
				case 28 -> {current = Encodings.UPPER; latched = false;}
				case 29 -> current = Encodings.MIXED;
				case 30 -> current = Encodings.DIGIT;
				case 31 -> {current = Encodings.BYTE; latched = false;}
			}
		}
		return true;
	}

	boolean handleMixed( int value ) {
		if (value == 0) {
			current = Encodings.PUNCT;
			latched = false;
		} else if (value == 1) {
			workString.append(' ');
		} else if (value <= 14) {
			workString.append((char)(value - 1));
		} else if (value <= 19) {
			workString.append((char)(value - 19 + 27));
		} else if (value == 20) {
			workString.append('@');
		} else if (value == 21) {
			workString.append('\\');
		} else if (value <= 24) {
			workString.append((char)(value - 22 + 94));
		} else {
			switch (value) {
				case 25 -> workString.append('|');
				case 26 -> workString.append('~');
				case 27 -> workString.append((char)127);
				case 28 -> current = Encodings.LOWER;
				case 29 -> current = Encodings.UPPER;
				case 30 -> current = Encodings.PUNCT;
				case 31 -> {current = Encodings.BYTE; latched = false;}
			}
		}
		return true;
	}

	boolean handlePunct( int value ) {
		if (value == 0) {
			if (verbose != null) verbose.println("FlG(n) encountered");
			return false;
		}
		switch (value) {
			case 1 -> workString.append('\r');
			case 2 -> workString.append("\r\n");
			case 3 -> workString.append(". ");
			case 4 -> workString.append(", ");
			case 5 -> workString.append(": ");
			case 27 -> workString.append('[');
			case 28 -> workString.append(']');
			case 29 -> workString.append('{');
			case 30 -> workString.append('}');
			case 31 -> current = Encodings.UPPER;
			default -> {
				if (value <= 20) {
					workString.append((char)(value - 6 + 33));
				} else if (value <= 26) {
					workString.append((char)(value - 21 + 58));
				}
			}
		}
		return true;
	}

	boolean handleDigit( int value ) {
		if (value == 0) {
			current = Encodings.PUNCT;
			latched = false;
		} else if (value == 1) {
			workString.append(' ');
		} else if (value <= 11) {
			workString.append((char)(value - 2 + 48));
		} else {
			switch (value) {
				case 12 -> workString.append(',');
				case 13 -> workString.append('.');
				case 14 -> current = Encodings.UPPER;
				case 15 -> {current = Encodings.UPPER; latched = false;}
			}
		}
		return true;
	}

	@Override public void setVerbose( @Nullable PrintStream out, @Nullable Set<String> set ) {
		this.verbose = BoofMiscOps.addPrefix(this, out);
	}
}
