package com.payceylon.app.data

import com.payceylon.app.data.local.WalletCache
import com.payceylon.app.data.local.WalletSnapshot

/** In-memory [WalletCache] for unit tests; the Room one is covered by an instrumented test. */
class FakeWalletCache(var snapshot: WalletSnapshot? = null) : WalletCache {
    var saves = 0

    override suspend fun load(): WalletSnapshot? = snapshot

    override suspend fun save(snapshot: WalletSnapshot) {
        this.snapshot = snapshot
        saves++
    }

    override suspend fun clear() {
        snapshot = null
    }
}
