//! A circular byte buffer allowing one producer and one consumer thread.
//!
//! Port of the Kotlin `ByteQueue` implementation, using `std::sync::Mutex` and
//! `Condvar` instead of Java/Kotlin `synchronized` blocks with `wait()`/`notify()`.

use std::sync::{Arc, Condvar, Mutex};

struct QueueState {
    buffer: Vec<u8>,
    head: usize,
    stored_bytes: usize,
    open: bool,
}

/// A lock-free-style SPSC (Single Producer, Single Consumer) ring buffer.
///
/// Thread safety is achieved via `Arc<Mutex<QueueState>>` + `Condvar`, which
/// mirrors the Kotlin/JVM `synchronized`/`wait()`/`notify()` pattern.
///
/// `ByteQueue` is cheap to clone (clones share the same underlying state) and
/// can be sent across thread boundaries.
#[derive(Clone)]
pub struct ByteQueue {
    inner: Arc<(Mutex<QueueState>, Condvar)>,
}

impl ByteQueue {
    /// Create a new `ByteQueue` with the given buffer capacity.
    pub fn new(size: usize) -> Self {
        Self {
            inner: Arc::new((
                Mutex::new(QueueState {
                    buffer: vec![0u8; size],
                    head: 0,
                    stored_bytes: 0,
                    open: true,
                }),
                Condvar::new(),
            )),
        }
    }

    /// Mark the queue as closed and wake any threads blocked on `read`/`write`.
    ///
    /// After closing, `read` returns `-1` and `write` returns `false`.
    pub fn close(&self) {
        let (lock, cvar) = &*self.inner;
        let mut state = lock.lock().expect("ByteQueue lock poisoned");
        state.open = false;
        cvar.notify_all();
    }

    /// Read up to `buf.len()` bytes from the queue into `buf`.
    ///
    /// Returns:
    /// - `-1` if the queue has been closed.
    /// - `0` if the queue is empty and `block` is `false`.
    /// - The number of bytes actually read (may be less than `buf.len()`).
    ///
    /// When `block` is `true`, this method sleeps until data is available or
    /// the queue is closed.
    pub fn read(&self, buf: &mut [u8], block: bool) -> i32 {
        let (lock, cvar) = &*self.inner;
        let mut state = lock.lock().expect("ByteQueue lock poisoned");

        // Wait while the buffer is empty and the queue is still open.
        while state.stored_bytes == 0 && state.open {
            if block {
                state = cvar.wait(state).expect("ByteQueue condvar wait failed");
            } else {
                return 0;
            }
        }

        if !state.open {
            return -1;
        }

        let mut total_read: i32 = 0;
        let buffer_length = state.buffer.len();
        let was_full = buffer_length == state.stored_bytes;
        let mut length = buf.len();
        let mut offset = 0usize;

        while length > 0 && state.stored_bytes > 0 {
            // Number of bytes available in the current contiguous run from head.
            let one_run = std::cmp::min(buffer_length - state.head, state.stored_bytes);
            let bytes_to_copy = std::cmp::min(length, one_run);

            // System.arraycopy(mBuffer, head, buffer, offset, bytesToCopy)
            buf[offset..offset + bytes_to_copy]
                .copy_from_slice(&state.buffer[state.head..state.head + bytes_to_copy]);

            state.head += bytes_to_copy;
            if state.head >= buffer_length {
                state.head = 0;
            }
            state.stored_bytes -= bytes_to_copy;
            length -= bytes_to_copy;
            offset += bytes_to_copy;
            total_read += bytes_to_copy as i32;
        }

        // If the buffer was full before reading, wake a blocked writer.
        if was_full {
            cvar.notify_one();
        }

        total_read
    }

    /// Write bytes from `buf[offset..offset+length]` into the queue.
    ///
    /// Returns `true` if all bytes were written, `false` if the queue was
    /// closed before all bytes could be written.
    ///
    /// If the buffer is full and the queue is open, this method blocks until
    /// space becomes available.
    ///
    /// # Panics
    ///
    /// Panics if `length + offset > buf.len()` or `length <= 0`.
    pub fn write(&self, buf: &[u8], offset: usize, length: usize) -> bool {
        assert!(
            length + offset <= buf.len(),
            "length + offset ({}) > buf.len() ({})",
            length + offset,
            buf.len()
        );
        assert!(length > 0, "length must be > 0");

        let (lock, cvar) = &*self.inner;
        let mut state = lock.lock().expect("ByteQueue lock poisoned");
        let buffer_length = state.buffer.len();

        let mut current_offset = offset;
        let mut current_length = length;

        while current_length > 0 {
            // Wait while the buffer is full and the queue is still open.
            while buffer_length == state.stored_bytes && state.open {
                state = cvar.wait(state).expect("ByteQueue condvar wait failed");
            }

            if !state.open {
                return false;
            }

            let was_empty = state.stored_bytes == 0;
            let mut bytes_to_write =
                std::cmp::min(current_length, buffer_length - state.stored_bytes);
            current_length -= bytes_to_write;

            // Copy bytes into the ring buffer in up to two contiguous runs.
            while bytes_to_write > 0 {
                let mut tail = state.head + state.stored_bytes;
                let one_run = if tail >= buffer_length {
                    tail -= buffer_length;
                    state.head - tail
                } else {
                    buffer_length - tail
                };

                let bytes_to_copy = std::cmp::min(one_run, bytes_to_write);

                // System.arraycopy(buffer, currentOffset, mBuffer, tail, bytesToCopy)
                state.buffer[tail..tail + bytes_to_copy]
                    .copy_from_slice(&buf[current_offset..current_offset + bytes_to_copy]);

                current_offset += bytes_to_copy;
                bytes_to_write -= bytes_to_copy;
                state.stored_bytes += bytes_to_copy;
            }

            // If the buffer was empty before writing, wake a blocked reader.
            if was_empty {
                cvar.notify_one();
            }
        }

        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::thread;

    #[test]
    fn test_basic_write_read() {
        let q = ByteQueue::new(64);
        let data = b"hello world";
        assert!(q.write(data, 0, data.len()));

        let mut buf = [0u8; 64];
        let n = q.read(&mut buf, false);
        assert_eq!(n, data.len() as i32);
        assert_eq!(&buf[..data.len()], data);
    }

    #[test]
    fn test_read_empty_non_blocking() {
        let q = ByteQueue::new(64);
        let mut buf = [0u8; 64];
        assert_eq!(q.read(&mut buf, false), 0);
    }

    #[test]
    fn test_close_returns_error() {
        let q = ByteQueue::new(64);
        q.close();
        let mut buf = [0u8; 64];
        assert_eq!(q.read(&mut buf, false), -1);
    }

    #[test]
    fn test_write_after_close() {
        let q = ByteQueue::new(64);
        q.close();
        assert!(!q.write(b"test", 0, 4));
    }

    #[test]
    fn test_wraparound() {
        let q = ByteQueue::new(8);
        let mut buf = [0u8; 8];

        // Fill partially, read it out to advance head.
        assert!(q.write(b"abcd", 0, 4));
        let n = q.read(&mut buf, false);
        assert_eq!(n, 4);

        // Now write enough to wrap around the ring buffer.
        assert!(q.write(b"12345678", 0, 8));
        let n = q.read(&mut buf, false);
        assert_eq!(n, 8);
        assert_eq!(&buf[..8], b"12345678");
    }

    #[test]
    fn test_blocking_read_threaded() {
        let q = ByteQueue::new(64);
        let q2 = q.clone();

        let reader = thread::spawn(move || {
            let mut buf = [0u8; 64];
            let n = q2.read(&mut buf, true);
            assert_eq!(n, 3);
            assert_eq!(&buf[..3], b"xyz");
        });

        thread::sleep(std::time::Duration::from_millis(20));
        assert!(q.write(b"xyz", 0, 3));
        reader.join().unwrap();
    }

    #[test]
    fn test_blocking_write_threaded() {
        let q = ByteQueue::new(4);
        let q2 = q.clone();

        let writer = thread::spawn(move || {
            // This will block until space is available.
            assert!(q2.write(b"abcdefgh", 0, 8));
        });

        // Drain the queue after a short delay to unblock the writer.
        thread::sleep(std::time::Duration::from_millis(20));
        let mut buf = [0u8; 4];
        q.read(&mut buf, false);
        thread::sleep(std::time::Duration::from_millis(20));
        q.read(&mut buf, false);

        writer.join().unwrap();
    }

    #[test]
    fn test_close_wakes_reader() {
        let q = ByteQueue::new(64);
        let q2 = q.clone();

        let reader = thread::spawn(move || {
            let mut buf = [0u8; 64];
            let n = q2.read(&mut buf, true);
            assert_eq!(n, -1);
        });

        thread::sleep(std::time::Duration::from_millis(20));
        q.close();
        reader.join().unwrap();
    }

    #[test]
    fn test_close_wakes_writer() {
        let q = ByteQueue::new(2);
        let q2 = q.clone();

        // Fill the buffer first.
        assert!(q.write(b"ab", 0, 2));

        let writer = thread::spawn(move || {
            // This will block because the buffer is full.
            let result = q2.write(b"cd", 0, 2);
            assert!(!result);
        });

        thread::sleep(std::time::Duration::from_millis(20));
        q.close();
        writer.join().unwrap();
    }
}
