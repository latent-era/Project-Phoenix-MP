package com.devil.phoenixproject.util

import platform.Foundation.NSRecursiveLock

/**
 * Global recursive lock for iOS.
 *
 * NSRecursiveLock is used so that nested calls from the same thread do not deadlock.
 * A single global instance is intentional: per-object lock maps would leak memory
 * because Kotlin/Native has no weak-reference-based cleanup for the map keys.
 */
@PublishedApi
internal val globalLock = NSRecursiveLock()

actual inline fun <T> withPlatformLock(lock: Any, block: () -> T): T {
    globalLock.lock()
    try {
        return block()
    } finally {
        globalLock.unlock()
    }
}
