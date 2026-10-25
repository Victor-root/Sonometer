# DJ Meter 🎵 - Decibel Meter App

A modern, feature-rich decibel meter application built with Jetpack Compose that helps you measure and visualize sound levels in real-time. 🎚️

## Features 🌟

- **Sound Level Meter Engine** 🔊
  - A, C and Z frequency weightings (IEC 61672-1, Class 1 tolerances at 44.1/48 kHz)
  - Fast, Slow and Impulse time weightings, applied per sample
  - Leq (energy average), Lmin, Lmax and peak, all weightings measured simultaneously
  - Overload indicator, start-up transient and silenced capture excluded from statistics
  - Raw microphone capture (UNPROCESSED, then VOICE_RECOGNITION) with nominal Android CDD sensitivity
  - Calibration against a reference sound level meter, stored per microphone source
  - Range: 20-130 dB SPL

- **Professional UI/UX** 💅
  - Material Design 3 implementation
  - Dark theme optimized
  - Smooth animations
  - Responsive layout
  - Intuitive controls

- **Visual Feedback** 📊
  - Dynamic progress bar
  - Real-time graph visualization
  - Color-coded intensity indicators
  - Pulsating record button

- **Technical Information** ⚙️
  - Sampling rate display
  - Channel configuration
  - Sound intensity description
  - Update rate information

- **Data Visualization** 📈
  - Bottom sheet graph view
  - Last 50 readings history
  - Smooth line graph
  - Grid lines for better readability

## Technical Details 🛠️

- **Architecture**
  - MVVM Design Pattern
  - Clean Architecture
  - Repository Pattern
  - Dependency Injection

- **Technologies Used**
  - Kotlin
  - Jetpack Compose
  - Material 3
  - Android Audio API
  - Kotlin Coroutines
  - StateFlow

## Permission Requirements 🔐

- Microphone access required
- Runtime permission handling
- Graceful permission state management

## Installation 📱

1. Clone the repository
2. Open in Android Studio
3. Build and run on your device

## TODO List for Contributors 🚀

Here are some exciting features that could be added to the app:

1. **Sound Recording Feature** 📝
   - Add ability to record audio
   - Save recordings with timestamps
   - Export recordings in different formats

2. **Advanced Analytics** 📊
   - Frequency spectrum analysis
   - Sound pattern recognition
   - Noise pollution alerts

3. **Calibration Tools** 🎯
   - Device-specific calibration
   - Reference level adjustment
   - Professional calibration mode

4. **Social Features** 🤝
   - Share readings and graphs
   - Compare measurements with friends
   - Community-driven noise map

5. **Environmental Impact** 🌍
   - Noise pollution awareness
   - Daily/weekly noise reports
   - Health impact indicators

## Contributing 🤝

Contributions are welcome! Feel free to:
- Fork the repository
- Create a feature branch
- Submit a pull request

## License 📄

This project is licensed under the MIT License - see the LICENSE file for details.

## Credits 💖

Created with ❤️ by Ayan 🇮🇳

## Screenshots 📱

[Add your app screenshots here]

## Contact 📧

For any queries or suggestions, feel free to reach out!

---

Made with Jetpack Compose 🚀 