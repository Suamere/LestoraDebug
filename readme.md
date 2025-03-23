# Lestora Debugger

This is a standalone client mod that allows other mods to take control of a new custom F3 screen.  By default, this will show coords, facing direction, and block light level.  Let me know if you think it should show more by default.

Other mods (Lestora or otherwise) can use this mod to "subscribe" some of their inner mod detail so that it can be conditionally seen in realtime.  I use this in other mods for things like showing the player's wetness and temperature while developing.

## Features
- **Usage:** Press F3 to turn the debug screen on and off.  This operates different depending upon whether the Minecraft F3 screen is enabled or not.
- **Priority:** The higher the number, the more the priority.  1 would therefore be the lowest/last priority, and 0 means it would be disabled.

## Manual Installation
1. Download the mod JAR from CurseForge.
2. Place the JAR file into your `mods` folder.
3. Launch Minecraft with the Forge profile.

## Commands
- Use the command `/lestora debug enableF3 [true/false]` to re-enable the Minecraft F3 screen. Default when this mod is installed is disabled (enableF3 false).
- Use the command `/lestora debug list` to show keys and priority for the debug values.  This may give you insight into how other mods are using it in case you want to hook in yourself.
- Use the command `/lestora debug enableHud [true/false]` to turn on "hud mode".  This just means that when "F3 is off", it still shows limited (higher priority) information at the top of the screen.

## Compatibility
- **Minecraft Version:** 1.21.4
- **Forge Version:** 54.1.0

## Troubleshooting
If you run into issues (e.g., crashes or unexpected behavior), check the logs in your `crash-reports` or `logs` folder. You can also open an issue on the mod’s GitHub repository.

## Contributing
Contributions are welcome! Please submit pull requests or open issues if you have suggestions or bug reports.

## License
This project is licensed under the MIT License.
