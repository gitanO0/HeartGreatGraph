# HeartGreatGraph ❤️📈

[![Latest Release](https://img.shields.io/github/v/release/gitanO0/HeartGreatGraph?style=for-the-badge&color=blue)](https://github.com/gitanO0/HeartGreatGraph/releases/latest)
[![Download APK](https://img.shields.io/github/downloads/gitanO0/HeartGreatGraph/total?style=for-the-badge&color=green&label=Download%20APK)](https://github.com/gitanO0/HeartGreatGraph/releases/latest)
[![Build Status](https://img.shields.io/github/actions/workflow/status/gitanO0/HeartGreatGraph/build-apk.yml?style=for-the-badge)](https://github.com/gitanO0/HeartGreatGraph/actions)

Welcome to **HeartGreatGraph**! This is a straight-forward, privacy-first, on-device Android application designed to give you clear and interactive insights into your heart rate data over the last 24 hours.

Using the official Android **Health Connect API**, HeartGreatGraph securely reads your existing heart rate measurements (whether they come from your smartwatch, fitness tracker, or phone) and visualizes them in an elegant, easy-to-read chart.

## Features ✨

*   **24-Hour Rolling View:** Instantly see your heart rate trends from exactly 24 hours ago up to this very minute.
*   **Interactive Scrubber:** Long-press anywhere on the graph to bring up the precision scrubber! Drag it left and right to see the exact time and BPM (Beats Per Minute) for any point on the chart.
*   **Pinch-to-Zoom & Pan:** Pinch the chart to zoom in for minute-by-minute details, or drag to pan smoothly through your day's history.
*   **Dynamic Scaling:** The graph automatically adjusts its Y-axis (BPM scale) and X-axis (Time scale) to perfectly fit the data currently visible on your screen.
*   **Privacy First:** Your data never leaves your device. HeartGreatGraph only asks for read-access to your local Health Connect data to draw the graph on your screen. No accounts, no cloud sync, no tracking.

## Getting Started 🚀

### Prerequisites
*   An Android device running **Android 14 (API 34)** or higher.
*   The **Health Connect** app must be installed (it comes pre-installed on Android 14+).
*   Another app or device (like a smartwatch) that writes Heart Rate data to Health Connect.

### Installation
The easiest way to get the app is to download the latest APK from the releases page!

1.  Click the **[Download APK](https://github.com/gitanO0/HeartGreatGraph/releases/latest)** badge at the top of this page.
2.  Download the `app-debug.apk` file to your phone.
3.  Open the file and tap **Install** (you may need to allow "Install unknown apps" from your browser/file manager).
4.  Open HeartGreatGraph, grant the requested Health Connect permissions, and enjoy your data!

## How it Works 🛠️

HeartGreatGraph is built entirely with modern Android development tools:
*   **100% Kotlin**
*   **Jetpack Compose** for a fluid, reactive, and declarative User Interface.
*   **Health Connect Client** to securely interface with Android's unified health data store.
*   **Custom Canvas Drawing:** The entire graph, grid lines, and interactive scrubber are custom-drawn directly onto a Compose Canvas for maximum performance and customization.

## Building from Source

If you want to build the project yourself using Android Studio:
1. Clone the repository: `git clone https://github.com/gitanO0/HeartGreatGraph.git`
2. Open the project in Android Studio (Koala or newer recommended).
3. Build and Run! (Note: The app requires API 34+ to run).

---
*Created by Royce.*
