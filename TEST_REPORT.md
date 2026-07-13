# Test report

Project: HandWall Balloon Blaster 1.0.0

## Completed checks

The pure Kotlin gesture, recoil-trigger, balloon, bullet, collision, particle and calibration logic was compiled with `kotlinc` and the complete deterministic harness was executed five separate times. All five runs passed.

Each run covered:

1. Finger-gun pose recognition.
2. Rejection of an open hand.
3. Upward recoil firing.
4. Prevention of repeated firing while the recoil remains raised.
5. Trigger reset and second shot after cooldown.
6. Preservation of the pre-recoil aiming direction.
7. Fast bullet/balloon collision and pop event.
8. Particle generation after a hit.
9. Calibration mirroring.
10. An 18,000-frame stress simulation with 28 balloons and repeated shots, checking that every position and velocity remains finite.

Additional checks passed:

- Eight Android XML resources parsed successfully.
- GitHub Actions YAML parsed successfully.
- The generated WAV is valid 44.1 kHz, 16-bit mono audio and contains a non-silent signal.
- Required source files, workflow, model-download step and hidden `.github` folder are present.
- The MediaPipe model is deliberately excluded from the ZIP and is downloaded during the GitHub build.
- Pure Kotlin JUnit test source compiled against test stubs.
- Kotlin source received a syntax parse check; no malformed tokens or missing syntax were found.
- The final ZIP is CRC-checked after creation.

## Hardware limitation

This environment cannot physically run an Android rear camera, Smart View receiver or projector. The included GitHub Actions workflow performs the full Android dependency resolution, runs the unit tests five times and builds the APK. Final camera calibration and recoil sensitivity must be adjusted on the user's own phone and projector because camera position, projection delay, lighting and hand size vary by setup.
