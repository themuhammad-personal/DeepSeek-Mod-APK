# DeepSeek Mod APK

A custom Morphe Patcher project for building native Android feature patches for the official DeepSeek application.

This repository uses the user's Super DeepSeek project as a **feature and UX reference**, not as a bundle of web code to inject into the native app. Browser-only modules are being redesigned as Android-native DEX extensions and minimal Morphe patches.

> Independent project. Not affiliated with, endorsed by, or distributed by DeepSeek or Morphe. The official DeepSeek APK is not included.

## Current target

| Property | Inspected sample |
|---|---|
| Package | com.deepseek.chat |
| Version | 2.7.4 (versionCode 293) |
| Supported Morphe target | 2.7.4 only |
| APK SHA-256 | a868bad448c87444f6c825b5ddbfa23756ad34178ab08503d107ff8e29b4f022 |

See [Target APK inspection](docs/TARGET-APK.md) before using a different release.

## What is in the first milestone

- Morphe Patcher project using the official template/plugin layout.
- Version-specific fingerprints for two statically identified DeepSeek methods.
- A compatibility gate that resolves those methods but changes no app behaviour.
- An optional launcher label patch ("Super DeepSeek").
- A minimal DEX extension module for future native runtime code.
- GitHub Actions build that publishes the generated MPP bundle as a workflow artifact.

The agent, Linux environment, MCP, native Generative UI, memory, projects and research features are **not implemented yet**. Their Android port plan is in [Feature port map](docs/FEATURE-PORT-MAP.md).

## Build

Requirements: JDK 21, Gradle 9.8.2, and access to the Morphe GitHub Packages registry.

Add a GitHub token with read:packages to your Gradle user properties:

    gpr.user = YOUR_GITHUB_USERNAME
    gpr.key = YOUR_TOKEN

Then run:

    gradle --no-daemon buildAndroid

The patch bundle is generated under:

    patches/build/libs/*.mpp

The DEX extension is compiled by the same Gradle build. Do not commit generated APK, MPP or MPE files.

## Apply locally

Use Morphe Desktop/Manager with the generated MPP and your own legally obtained DeepSeek APK. Start with the 2.7.4 sample declared above. Keep the official installation and its data intact until the patched build has been separately tested.

No original APK, decoded APK sources, account credentials, signing keys or build outputs are stored here.

## Project map

- [Architecture](docs/ARCHITECTURE.md)
- [Target APK evidence](docs/TARGET-APK.md)
- [Native feature port map](docs/FEATURE-PORT-MAP.md)
- [Security boundaries](docs/SECURITY-BOUNDARIES.md)

## References

- [Morphe Patcher documentation](https://github.com/MorpheApp/morphe-patcher)
- [Morphe custom patch template](https://github.com/MorpheApp/morphe-patches-template)
- [Super DeepSeek feature reference](https://github.com/themuhammad-personal/Super-Deepseek-)
