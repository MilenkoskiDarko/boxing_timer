# Boxing Timer

Native Android app (Kotlin, XML) — set number of rounds, round length(minutes and seconds) and
rest length(minutes and seconds).
Runs a foreground service so it keeps counting and playing sounds even
if the screen locks in the gym.

-Imported sound for bell as round finisher,round starter and workout finisher.
-Imported sound for 5-seconds warning before starting and ending a round.

##IDLE
<img width="572" height="741" alt="Screenshot 2026-09-27 at 10 26 57" src="https://github.com/user-attachments/assets/3aa55223-32f7-4eea-bd31-4d14e294011a" />

##ROUND
<img width="572" height="741" alt="Screenshot 2026-09-27 at 10 27 53" src="https://github.com/user-attachments/assets/bca588ca-a3df-4856-8cac-8f4cc771a279" />

##REST
<img width="572" height="741" alt="Screenshot 2026-09-27 at 10 28 22" src="https://github.com/user-attachments/assets/9ccb87a7-f3c3-49d6-9912-ff65e29c24a0" />

##WORKOUT COMPLETE
<img width="572" height="741" alt="Screenshot 2026-09-27 at 10 33 51" src="https://github.com/user-attachments/assets/3bd3cb0a-9b70-40e5-a4a0-ab70e0b2d0be" />




## How it works

- `MainActivity` — takes your inputs (rounds, round time, rest time), shows the
  live countdown, and has Start/Pause/Reset.
- `TimerService` — a foreground service that owns the actual `CountDownTimer`,
  plays sounds via `SoundPool`, vibrates on phase changes, holds a partial
  wake lock, and shows a persistent notification. It broadcasts tick updates
  back to the activity via `LocalBroadcastManager`.
- `TimerPhase` — simple enum: `IDLE`, `ROUND`, `REST`, `FINISHED`.
