# Implementation Plan

1. Add `shareHotspot=true` state and persistence; map it to fixed Wi-Fi hotspot interface prefixes.
2. Force legacy advanced state to safe constants during load and remove old keys during save.
3. Replace DNS sheet with validated primary/secondary address fields.
4. Replace Core/Advanced/TPROXY settings sections with a compact Network section.
5. Enforce an IPv6 OUTPUT/FORWARD kill switch when IPv6 support is disabled.
6. Update strings and CI static assertions.
7. Commit and push to `main`; monitor and fix GitHub Actions until green.
8. Download and inspect the generated APK artifact.
