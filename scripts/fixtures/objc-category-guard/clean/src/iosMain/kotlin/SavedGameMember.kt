import platform.GameKit.GKSavedGame

// GKSavedGame declares this directly; only GKLocalPlayer saved-game category methods need imports.
fun loadSavedGame(saved: GKSavedGame) {
    saved.loadDataWithCompletionHandler { _, _ -> }
}
