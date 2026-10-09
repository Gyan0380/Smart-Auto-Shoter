# GitHub build instructions

1. Extract the contents of this ZIP.
2. Upload the project files and folders to the root of your GitHub repository.
3. Ensure `.github/workflows/build.yml` is included.
4. Push to `main`/`master`, or open the Actions tab and run **Build Smart Auto Sorter**.
5. Download `smart-auto-sorter-build` from the completed workflow run's Artifacts section.
6. If the build fails, open the logs and fix the actual Gradle/compiler/API errors before using the mod.

Important: this workflow automates the build; it does not guarantee compilation. A successful build is not the same as a successful in-game test. Test in a separate Minecraft instance/world first.
