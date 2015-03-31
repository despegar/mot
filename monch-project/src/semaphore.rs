use std::sync::{Mutex, Condvar};
use time::precise_time_ns;
use std::time::duration::Duration;

/// A counting, blocking, semaphore.
///
/// Semaphores are a form of atomic counter where access is only granted if the
/// counter is a positive value. Each acquisition will block the calling thread
/// until the counter is positive, and each release will increment the counter
/// and unblock any threads if necessary.
///
/// # Examples
///
/// ```
/// use std::sync::Semaphore;
///
/// // Create a semaphore that represents 5 resources
/// let sem = Semaphore::new(5);
///
/// // Acquire one of the resources
/// sem.acquire();
///
/// // Release our initially acquired resource
/// sem.release();
/// ```
pub struct Semaphore {
    lock: Mutex<isize>,
    cvar: Condvar,
}

impl Semaphore {
    /// Creates a new semaphore with the initial count specified.
    ///
    /// The count specified can be thought of as a number of resources, and a
    /// call to `acquire` or `access` will block until at least one resource is
    /// available. It is valid to initialize a semaphore with a negative count.
    pub fn new(count: isize) -> Semaphore {
        Semaphore {
            lock: Mutex::new(count),
            cvar: Condvar::new(),
        }
    }

    /// Acquires a resource of this semaphore, blocking the current thread until
    /// it can do so.
    ///
    /// This method will block until the internal count of the semaphore is at
    /// least 1.
    pub fn acquire(&self) -> isize {
        let mut count = self.lock.lock().unwrap();
        while *count <= 0 {
            count = self.cvar.wait(count).unwrap();
        }
        *count -= 1;
        *count
    }

    pub fn acquire_timeout(&self, timeout: Duration) -> bool {
        let start = precise_time_ns();
        let mut count = self.lock.lock().unwrap();
        let mut remaining: Duration;
        while {
            let elapsed = Duration::nanoseconds((precise_time_ns() - start) as i64);
            remaining = timeout - elapsed;
            *count <= 0 && remaining > Duration::zero()
        } {
            let (c, _) = self.cvar.wait_timeout(count, remaining).unwrap();
            count = c;
        }
        if *count > 0 {
            *count -= 1;
            true
        } else {
            false
        }
    }

    /// Release a resource from this semaphore.
    ///
    /// This will increment the number of resources in this semaphore by 1 and
    /// will notify any pending waiters in `acquire` or `access` if necessary.
    pub fn release(&self) {
        *self.lock.lock().unwrap() += 1;
        self.cvar.notify_one();
    }

}
