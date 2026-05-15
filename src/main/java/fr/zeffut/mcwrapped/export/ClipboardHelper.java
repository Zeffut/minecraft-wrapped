package fr.zeffut.mcwrapped.export;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Cross-platform image-to-clipboard helper. Java AWT's {@code DataFlavor.imageFlavor} produces a
 * payload most apps don't recognize on macOS and Linux, so we shell out to native tools when we
 * can:
 * <ul>
 *   <li>macOS  → {@code osascript}</li>
 *   <li>Linux  → {@code xclip} or {@code wl-copy} (whichever is available)</li>
 *   <li>Windows → PowerShell + {@code System.Windows.Forms.Clipboard.SetImage}</li>
 * </ul>
 * If no native tool works, falls back to AWT.
 */
public final class ClipboardHelper {

    private ClipboardHelper() {}

    public static void copyImage(final BufferedImage image) throws IOException {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        // Save to a temp PNG once; native handlers all work from a file path.
        final Path temp = Files.createTempFile("mcwrapped-clip-", ".png");
        try {
            ImageIO.write(image, "PNG", temp.toFile());
            if (os.contains("mac")) {
                copyMacOS(temp);
            } else if (os.contains("win")) {
                copyWindows(temp);
            } else {
                copyLinux(temp, image);
            }
        } finally {
            try { Files.deleteIfExists(temp); } catch (final IOException ignore) {}
        }
    }

    // ------------------------------------------------------------------------

    private static void copyMacOS(final Path png) throws IOException {
        // «class PNGf» = the macOS pasteboard class for PNG data.
        // AppleScript string literals escape `\` and `"` with backslashes.
        final String escaped = png.toAbsolutePath().toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        final String script = "set the clipboard to (read (POSIX file \""
                + escaped + "\") as «class PNGf»)";
        runOrFail(new ProcessBuilder("osascript", "-e", script));
    }

    private static void copyWindows(final Path png) throws IOException {
        // PowerShell single-quoted strings: only `'` needs escaping (doubled). Reject newlines
        // defensively so a hostile TMPDIR can't break out across statements.
        final String raw = png.toAbsolutePath().toString();
        if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
            throw new IOException("Refusing clipboard copy: temp path contains newlines.");
        }
        final String escaped = raw.replace("'", "''");
        final String ps = String.join(";",
                "Add-Type -AssemblyName System.Windows.Forms",
                "Add-Type -AssemblyName System.Drawing",
                "$img = [System.Drawing.Image]::FromFile('" + escaped + "')",
                "[System.Windows.Forms.Clipboard]::SetImage($img)",
                "$img.Dispose()");
        runOrFail(new ProcessBuilder("powershell", "-NoProfile", "-Command", ps));
    }

    private static void copyLinux(final Path png, final BufferedImage image) throws IOException {
        // Try wl-copy (Wayland) first since most modern distros use it; fall back to xclip; then AWT.
        if (tryRun(new ProcessBuilder("wl-copy", "--type", "image/png").redirectInput(png.toFile()))) return;
        if (tryRun(new ProcessBuilder("xclip", "-selection", "clipboard", "-t", "image/png", "-i", png.toAbsolutePath().toString()))) return;
        // Last resort: AWT image flavor. Works for at least some Linux apps.
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(image), null);
    }

    // ------------------------------------------------------------------------

    private static void runOrFail(final ProcessBuilder pb) throws IOException {
        pb.redirectErrorStream(true);
        try {
            final Process p = pb.start();
            // Drain stdout so the process can exit cleanly.
            try (var in = p.getInputStream()) {
                in.readAllBytes();
            }
            final int exit = p.waitFor();
            if (exit != 0) {
                throw new IOException(pb.command().get(0) + " exited with " + exit);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while copying", e);
        }
    }

    private static boolean tryRun(final ProcessBuilder pb) {
        try {
            runOrFail(pb);
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    private static final class ImageTransferable implements Transferable {
        private final Image image;

        ImageTransferable(final Image image) { this.image = image; }

        @Override
        public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.imageFlavor}; }

        @Override
        public boolean isDataFlavorSupported(final DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }

        @Override
        public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException {
            if (!DataFlavor.imageFlavor.equals(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }
}
