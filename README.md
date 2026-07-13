# HandWall Balloon Blaster

An offline Android projection game designed for an Android phone mirrored to a projector.
The rear camera tracks one hand with MediaPipe. Make a finger-gun pose, aim with the index
finger, then flick the fingertip slightly upward to launch a visible bullet. Balloons drift
slowly around the projected wall. A hit creates a particle burst and plays a generated
balloon-pop sound.

## Main features

- On-device MediaPipe hand landmark tracking
- Finger-gun pose recognition: index straight, thumb open, other fingers curled
- Upward fingertip-recoil trigger with sensitivity and cooldown controls
- Visible bullet, trail, aiming guide and muzzle flash
- Slowly drifting balloons with configurable count, speed and size
- Balloon burst particles, pop score and offline pop sound
- Smart View / screen-cast compatible full-screen black projection
- Hand/projector alignment controls: mirror, X/Y scale, X/Y offset and rotation
- Touch test mode and automatic Test Shot button
- Long-press or Volume Down to reopen hidden controls
- No server, account, advertisement or paid runtime service

## Build

The model is intentionally not stored in the ZIP. The GitHub Actions workflow downloads
the official MediaPipe `hand_landmarker.task`, runs unit tests five times, builds a debug
APK and publishes it as `HandWall-Balloon-Blaster-APK`.

Open `GITHUB_CODESPACE_STEPS.txt` for phone/tablet instructions.

## First-use calibration

1. Start Smart View or Screen Cast to the projector.
2. Fix the phone close to the projector lens, rear camera facing the wall.
3. Open the app and allow camera permission.
4. Show your finger-gun pose on the wall.
5. Adjust mirror, scale, offset and rotation until the green hand guide overlays your hand.
6. Confirm the blue line points exactly where your index finger points.
7. Aim at a balloon and flick the index fingertip upward slightly, then return it.
8. Reduce Recoil movement % if no shot occurs; increase it if accidental shots occur.
9. Press Start Show to hide the controls.

## Sound

The included `balloon_pop.wav` was synthesized specifically for this project and does not
use third-party audio. Keep Android media volume up. Depending on Smart View settings, the
sound may play through the projector/receiver or the phone.
