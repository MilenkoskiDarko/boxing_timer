# Boxing Timer

Native Android app (Kotlin, XML) — set number of rounds, round length(minutes and seconds) and
rest length(minutes and seconds).
Runs a foreground service so it keeps counting and playing sounds even
if the screen locks in the gym.


## How it works

- `MainActivity` — takes your inputs (rounds, round time, rest time), shows the
  live countdown, and has Start/Pause/Reset.
- `TimerService` — a foreground service that owns the actual `CountDownTimer`,
  plays sounds via `SoundPool`, vibrates on phase changes, holds a partial
  wake lock, and shows a persistent notification. It broadcasts tick updates
  back to the activity via `LocalBroadcastManager`.
- `TimerPhase` — simple enum: `IDLE`, `ROUND`, `REST`, `FINISHED`.
