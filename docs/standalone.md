# Diagnostic OpenGL test harness

[Русский](ru/standalone.md)

[Documentation](README.md) · [Project overview](../README.md)

The standalone example tests a hidden graphics context without Minecraft: Java 21 + LWJGL 3.3.6, OpenGL 3.3 core, and on-demand rendering into a separate 640×360 framebuffer.

## Building and running the harness

Requires JDK 21+ and Maven.

```powershell
cd C:\Projects\MinecraftMods\Offcraft
mvn.cmd -B -ntp package
java -jar dist\offcraft-1.0.0-standalone-windows-x64.jar
```

The executable JAR includes LWJGL and Windows x64 natives. It is written directly to the root `dist/`; Maven intermediates stay in `target/`. Version and naming rules: [development.md](development.md#versioning).

Enter commands in the same terminal:

```text
status
render
render output/frame with spaces.png
status
quit
```

- `render` — save `output/frame-0001.png`, then `frame-0002.png`, and so on.
- `render <path>` — save a PNG at the specified path. Spaces are allowed; quotation marks are not needed. Relative paths are resolved against the working directory.
- `status` — PID, window visibility, number of successfully saved frames, and render dimensions.
- `help` — brief command reference.
- `quit` or stdin EOF — terminate the process and release resources.

Numbering starts over in a new process; existing files with the same names are overwritten. The example is designed for one process in the working directory.

The launch terminal remains visible; the graphics window is not created in a visible state. A controller can launch Java with redirected stdin/stdout/stderr without a new console window and send the same commands through stdin. Closing stdin terminates the application.

## Harness behavior

All GLFW/OpenGL calls run on the main thread. A separate daemon thread reads stdin and passes commands through a bounded queue. While waiting for requests, the application processes events approximately every 50 ms, but does not draw or encode images.

Each successful render request changes the position of the colored triangle, the background color, and the marker in the upper-left corner. Odd-numbered frames have an orange marker and a triangle on the left; even-numbered frames have a light-blue marker and a triangle on the right. This makes it possible to identify a fresh frame and check its vertical orientation.

Images are read from the FBO through `glReadPixels`, not from the desktop. Rows are flipped for PNG output. The readback buffer and image are reused. `gpu_readback_ms` measures CPU wall-clock time for submitting rendering commands and synchronously reading pixels, not pure GPU time. `total_ms` also includes pixel conversion, PNG encoding, and writing the file. Render command errors are written to stderr; operation can continue afterward.

Historical harness results and verification limits: [verification.md](verification.md). Success of the harness does not establish that the Minecraft mod works.
