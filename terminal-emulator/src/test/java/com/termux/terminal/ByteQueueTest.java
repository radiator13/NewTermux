package com.termux.terminal;

import junit.framework.TestCase;

/** Tests for the Rust-backed byte queue accessed via RustJNI. */
public class ByteQueueTest extends TestCase {

	private static void assertArrayEquals(byte[] expected, byte[] actual) {
		if (expected.length != actual.length) {
			fail("Difference array length");
		}
		for (int i = 0; i < expected.length; i++) {
			if (expected[i] != actual[i]) {
				fail("Inequals at index=" + i + ", expected=" + (int) expected[i] + ", actual=" + (int) actual[i]);
			}
		}
	}

	public void testCompleteWrites() throws Exception {
		long q = RustJNI.termByteQueueNew(10);
		try {
			assertTrue(RustJNI.termByteQueueWrite(q, new byte[]{1, 2, 3}, 0, 3));

			byte[] arr = new byte[10];
			assertEquals(3, RustJNI.termByteQueueRead(q, arr, arr.length, true));
			assertArrayEquals(new byte[]{1, 2, 3}, new byte[]{arr[0], arr[1], arr[2]});

			assertTrue(RustJNI.termByteQueueWrite(q, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 10));
			assertEquals(10, RustJNI.termByteQueueRead(q, arr, arr.length, true));
			assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, arr);
		} finally {
			RustJNI.termByteQueueFree(q);
		}
	}

	public void testQueueWraparound() throws Exception {
		long q = RustJNI.termByteQueueNew(10);
		try {
			byte[] origArray = new byte[]{1, 2, 3, 4, 5, 6};
			byte[] readArray = new byte[origArray.length];
			for (int i = 0; i < 20; i++) {
				RustJNI.termByteQueueWrite(q, origArray, 0, origArray.length);
				assertEquals(origArray.length, RustJNI.termByteQueueRead(q, readArray, readArray.length, true));
				assertArrayEquals(origArray, readArray);
			}
		} finally {
			RustJNI.termByteQueueFree(q);
		}
	}

	public void testWriteNotesClosing() throws Exception {
		long q = RustJNI.termByteQueueNew(10);
		RustJNI.termByteQueueClose(q);
		assertFalse(RustJNI.termByteQueueWrite(q, new byte[]{1, 2, 3}, 0, 3));
		RustJNI.termByteQueueFree(q);
	}

	public void testReadNonBlocking() throws Exception {
		long q = RustJNI.termByteQueueNew(10);
		try {
			assertEquals(0, RustJNI.termByteQueueRead(q, new byte[128], 128, false));
		} finally {
			RustJNI.termByteQueueFree(q);
		}
	}

}
