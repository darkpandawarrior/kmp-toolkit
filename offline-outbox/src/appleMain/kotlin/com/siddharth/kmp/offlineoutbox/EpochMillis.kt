package com.siddharth.kmp.offlineoutbox

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** NSDate speaks seconds since the epoch; the shared contract is milliseconds. */
private const val MILLIS_PER_SECOND = 1000

actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()
