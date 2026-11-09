# SetEditPlus 2.0

Powerful Android Settings Editor (`System`, `Secure`, `Global`) with **Shizuku** and **Root** support.

## Highlights & Features
- **Material 3 Expressive UI**: Clean, adaptive interface with **OLED Pure Black** mode, table/status badges (`SYSTEM` / `SECURE` / `GLOBAL`, `MODIFIED` / `NEW` / `DELETED`), and stock-vs-current value diffing.
- **Dual Privileged Backends**: Works seamlessly with both **Root (`su`)** and **Shizuku** with strict exit-code and `stderr` verification.
- **Single-Shell Batch Execution Engine**: Executes boot restoration, bulk rollbacks, and snapshot imports inside a single shell session instead of spawning `O(N)` processes.
- **Smart Value Inspector & Safety Guard**:
  - Automatically detects setting types (`Boolean 0/1`, `Boolean true/false`, `Integer`, `Decimal/Scale`, `Component/Package`, `URI`, `String`) and category tags.
  - Provides one-tap **Quick Presets** for animation scales, display refresh rates, font scales, screen timeouts, and boolean flags.
  - Guards against brick/SystemUI-crash values (e.g., out-of-range forced DPI or invalid animation scales) before writing.
- **Bootloop Safe-Guard**: Automatically trips and disables Autostart if 3 rapid consecutive reboots are detected within 3 minutes.
- **Snapshots & Shell Script Studio**:
  - Export and import full JSON snapshots (including stock rollback values and pinned favorites) via Storage Access Framework or clipboard, with live diff preview before applying.
  - Generate standalone POSIX `.sh` scripts (`settings put` / `settings delete`) for `adb shell` or Magisk/KernelSU boot scripts.
- **Pinned Favorites & Instant Filtering**: Pin frequently used settings across tables and filter by `All`, `Favorites`, or `Modified`.

## Build Requirements
- **JDK**: 21+
- **Compile / Target SDK**: 37 (Android 17)
- **Min SDK**: 26 (Android 8.0)


## Warning
This is a pet project that I **maintain** very rarely (only when I need it myself). PRs and Issues are **closed**.
