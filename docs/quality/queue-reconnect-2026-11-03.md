# Queue and control response repair

Baseline: ed69752 (v1.1.3). A reconnect restored live media metadata but skipped QueueStore whenever the service already had a current item. Activity-local queue and index therefore stayed empty; Next returned immediately. A later transition could persist the empty queue. Saved playlists are not a queue until selected, and the Home mix's 50 is its candidate count, not a player queue count.

Changes: restore and reconcile the saved logical queue against the service's current media ID before attaching listeners; never overwrite saved state with an empty reconnect; retain actual live index; don't attach an unrelated playlist. Single live item is the safe fallback when previous queue data no longer exists. Play a mix/playlist again to restore full context in that case. No library, download or playlist reset.

Response: show selected song and loading state immediately, stop the old song while resolving, cancel superseded playback jobs, discard stale results, clear inherited resume positions when selecting another track. Actual extraction and buffering still depend on network/upstream; no phone-latency improvement is claimed. Recently played is recorded once the selected song actually plays, not on the loading selection. Background transitions continue to update the service-owned played-ID ledger.

Home: replace floating Refresh with a compact New picks pill beside Made for you; spinner plus disabled state while refreshing, separate edit-taste control.

Verification added: 50-track queue reconciliation unit cases; actual MediaSession service reconnect and Next instrumentation; actual Compose header render. Test results recorded separately once CI completes. No user screenshots or private user data in this public repository.
