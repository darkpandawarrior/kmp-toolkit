package com.siddharth.kmp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Real reachability via ConnectivityManager — validated internet capability, not just an up interface. */
class AndroidConnectivityChecker(
    private val context: Context,
) : ConnectivityChecker {
    override fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
