package offcraft;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** All GLFW and OpenGL work stays on the main thread; stdin only queues commands. */
public final class Offcraft implements AutoCloseable {
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private record Command(String line) {}

    private final BlockingQueue<Command> commands = new ArrayBlockingQueue<>(32);
    private final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
    private final int[] imagePixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    private GLFWErrorCallback errorCallback;
    private boolean initialized;
    private long window;
    private int framebuffer;
    private int texture;
    private int program;
    private int vertexArray;
    private int offsetLocation;
    private ByteBuffer pixels;
    private long frame;

    public static void main(String[] args) {
        try (Offcraft demo = new Offcraft()) {
            demo.initialize();
            demo.run();
        } catch (Exception e) {
            System.err.println("fatal: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void initialize() {
        errorCallback = GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed");
        }
        initialized = true;
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
        glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        window = glfwCreateWindow(WIDTH, HEIGHT, "Offcraft", 0, 0);
        if (window == 0) {
            throw new IllegalStateException("Hidden OpenGL 3.3 window creation failed");
        }
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glfwSwapInterval(0);

        // Never rely on the hidden window's default framebuffer dimensions.
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Offscreen framebuffer is incomplete");
        }

        program = glCreateProgram();
        int vertex = compile(GL_VERTEX_SHADER, """
                #version 330 core
                uniform float offset;
                out vec3 color;
                const vec2 positions[3] = vec2[3](
                    vec2(-0.55, -0.65), vec2(0.55, -0.65), vec2(0.0, 0.65));
                const vec3 colors[3] = vec3[3](
                    vec3(1.0, 0.25, 0.15), vec3(0.15, 1.0, 0.35), vec3(0.2, 0.45, 1.0));
                void main() {
                    gl_Position = vec4(positions[gl_VertexID] + vec2(offset, 0.0), 0.0, 1.0);
                    color = colors[gl_VertexID];
                }
                """);
        try {
            int fragment = compile(GL_FRAGMENT_SHADER, """
                    #version 330 core
                    in vec3 color;
                    out vec4 outputColor;
                    void main() { outputColor = vec4(color, 1.0); }
                    """);
            try {
                glAttachShader(program, vertex);
                glAttachShader(program, fragment);
                glLinkProgram(program);
                if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                    throw new IllegalStateException("Shader link failed: " + glGetProgramInfoLog(program));
                }
            } finally {
                glDeleteShader(fragment);
            }
        } finally {
            glDeleteShader(vertex);
        }
        offsetLocation = glGetUniformLocation(program, "offset");
        vertexArray = glGenVertexArrays();
        pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * 4);
        checkGl("initialization");
        System.out.printf("ready pid=%d visible=%s size=%dx%d renderer=%s OpenGL=%s%n",
                ProcessHandle.current().pid(), visible(), WIDTH, HEIGHT,
                glGetString(GL_RENDERER), glGetString(GL_VERSION));
        System.out.println("commands: render [PNG path], status, help, quit; EOF also exits");
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String error = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Shader compilation failed: " + error);
        }
        return shader;
    }

    private void run() throws InterruptedException {
        Thread input = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    commands.put(new Command(line));
                }
            } catch (Exception e) {
                System.err.println("stdin error: " + e.getMessage());
            } finally {
                try {
                    commands.put(new Command(null));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "stdin-reader");
        input.setDaemon(true);
        input.start();

        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            Command command = commands.poll(50, TimeUnit.MILLISECONDS);
            if (command == null) {
                continue; // No draw, readback or encoding while idle.
            }
            if (command.line() == null) {
                break;
            }
            String line = command.line().strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            switch (parts[0]) {
                case "quit" -> { return; }
                case "status" -> System.out.printf("status pid=%d visible=%s frames=%d size=%dx%d%n",
                        ProcessHandle.current().pid(), visible(), frame, WIDTH, HEIGHT);
                case "help" -> System.out.println("render [PNG path] | status | quit; paths may contain spaces (no quotes)");
                case "render" -> {
                    try {
                        Path output = parts.length == 2 ? Path.of(parts[1])
                                : Path.of("output", "frame-%04d.png".formatted(frame + 1));
                        render(output);
                    } catch (Exception e) {
                        System.err.println("render error: " + e.getMessage());
                    }
                }
                default -> System.err.println("unknown command: " + parts[0]);
            }
        }
    }

    private boolean visible() {
        return glfwGetWindowAttrib(window, GLFW_VISIBLE) == GLFW_TRUE;
    }

    private void render(Path output) throws Exception {
        long started = System.nanoTime();
        long nextFrame = frame + 1;
        boolean odd = (nextFrame & 1) != 0;
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClearColor(odd ? 0.08f : 0.04f, odd ? 0.10f : 0.19f, 0.16f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(program);
        glUniform1f(offsetLocation, odd ? -0.2f : 0.2f);
        glBindVertexArray(vertexArray);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        // Top-left marker makes orientation and alternating frames observable.
        glEnable(GL_SCISSOR_TEST);
        glScissor(0, HEIGHT - 24, 24, 24);
        glClearColor(odd ? 1.0f : 0.0f, odd ? 0.5f : 1.0f, odd ? 0.0f : 1.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        pixels.clear();
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        checkGl("render/readback");
        long readbackFinished = System.nanoTime();

        // OpenGL rows are bottom-up; PNG rows are top-down.
        for (int y = 0; y < HEIGHT; y++) {
            int sourceRow = (HEIGHT - 1 - y) * WIDTH * 4;
            int targetRow = y * WIDTH;
            for (int x = 0; x < WIDTH; x++) {
                int index = sourceRow + x * 4;
                imagePixels[targetRow + x] = ((pixels.get(index + 3) & 255) << 24)
                        | ((pixels.get(index) & 255) << 16)
                        | ((pixels.get(index + 1) & 255) << 8)
                        | (pixels.get(index + 2) & 255);
            }
        }
        Path absolute = output.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        if (!ImageIO.write(image, "png", absolute.toFile())) {
            throw new IllegalStateException("No PNG encoder available");
        }
        frame = nextFrame;
        long finished = System.nanoTime();
        System.out.printf("rendered frame=%d visible=%s gpu_readback_ms=%.3f total_ms=%.3f path=%s%n",
                frame, visible(), (readbackFinished - started) / 1_000_000.0,
                (finished - started) / 1_000_000.0, absolute);
    }

    private static void checkGl(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            throw new IllegalStateException(operation + " OpenGL error: 0x" + Integer.toHexString(error));
        }
    }

    @Override
    public void close() {
        if (pixels != null) {
            MemoryUtil.memFree(pixels);
        }
        if (window != 0) {
            if (program != 0) glDeleteProgram(program);
            if (vertexArray != 0) glDeleteVertexArrays(vertexArray);
            if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
            if (texture != 0) glDeleteTextures(texture);
            glfwDestroyWindow(window);
        }
        if (initialized) glfwTerminate();
        if (errorCallback != null) {
            glfwSetErrorCallback(null);
            errorCallback.free();
        }
    }
}
