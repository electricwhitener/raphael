package raphael.ui;

import java.awt.Component;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

// Windows' own Open and Save dialog, for Raphael running inside WSL (Linux on Windows).
// There, Java is a Linux program, so its "native" dialog is the Linux one: it starts in the Linux
// home folder, and its Desktop is not your Windows Desktop. A browser in the same situation shows
// Explorer, and so does Raphael: it runs a short PowerShell script that shows the dialog, then
// turns the Windows path it returns (C:\Users\…) into the Linux path Raphael can open
// (/mnt/c/Users/…) with WSL's wslpath command.
// While the dialog is open, Raphael's window keeps repainting but ignores clicks, as with any
// dialog: a SecondaryLoop keeps Swing's events flowing while this code waits for the answer.
// OOP: a utility class (static methods, private constructor); exception handling: every failure
// becomes an IOException, so the caller can fall back to Java's own dialog.
public final class WindowsFileDialog {

    private WindowsFileDialog() {
    }

    // True inside WSL, where Windows' PowerShell can be reached.
    public static boolean available() {
        return System.getenv("WSL_DISTRO_NAME") != null && new File("/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe").exists();
    }

    // Shows the dialog over owner. The file chosen (as a Linux path), or null if cancelled.
    public static File choose(Component owner, boolean forSaving, FileKind kind, File folder, String suggestedName)
            throws IOException {
        String script = script(forSaving, kind, windowsPath(folder), suggestedName);
        String[] answer = new String[1];
        IOException[] failure = new IOException[1];
        SecondaryLoop waiting = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        owner.setEnabled(false);
        Thread asking = new Thread(() -> {
            try {
                answer[0] = run(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-STA",
                        "-EncodedCommand", encode(script)));
            } catch (IOException e) {
                failure[0] = e;
            } finally {
                waiting.exit();
            }
        }, "windows file dialog");
        asking.start();
        waiting.enter(); // returns once the dialog is closed; the window repaints meanwhile
        owner.setEnabled(true);
        if (owner instanceof java.awt.Window) {
            ((java.awt.Window) owner).toFront();
        }
        if (failure[0] != null) {
            throw failure[0];
        }
        String chosen = answer[0].trim();
        return chosen.isEmpty() ? null : new File(run(List.of("wslpath", "-u", chosen)).trim());
    }

    // The PowerShell that shows the dialog and prints the chosen file. A form that stays on top
    // owns the dialog, so it opens in front of Raphael's window.
    static String script(boolean forSaving, FileKind kind, String folder, String suggestedName) {
        StringBuilder ps = new StringBuilder()
                .append("$ProgressPreference = 'SilentlyContinue'\n")
                .append("[Console]::OutputEncoding = [Text.Encoding]::UTF8\n")
                .append("Add-Type -AssemblyName System.Windows.Forms\n")
                .append("[Windows.Forms.Application]::EnableVisualStyles()\n")
                .append("$owner = New-Object Windows.Forms.Form -Property @{ TopMost = $true }\n");
        if (forSaving) {
            ps.append("$d = New-Object Windows.Forms.SaveFileDialog\n")
                    .append("$d.Title = 'Save As'\n")
                    .append("$d.AddExtension = $true\n")
                    .append("$d.OverwritePrompt = $true\n")
                    .append("$d.FileName = ").append(quote(suggestedName)).append('\n');
        } else {
            ps.append("$d = New-Object Windows.Forms.OpenFileDialog\n")
                    .append("$d.Title = 'Open'\n");
        }
        if (forSaving && kind.mainEnding() != null) {
            ps.append("$d.DefaultExt = ").append(quote(kind.mainEnding())).append('\n');
        }
        ps.append("$d.Filter = ").append(quote(kind.windowsFilter())).append('\n');
        if (folder != null) {
            ps.append("$d.InitialDirectory = ").append(quote(folder)).append('\n');
        }
        ps.append("if ($d.ShowDialog($owner) -eq [Windows.Forms.DialogResult]::OK) { [Console]::Out.Write($d.FileName) }\n");
        return ps.toString();
    }

    // A PowerShell string in single quotes, where a quote inside is written twice.
    static String quote(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    // -EncodedCommand takes the script as Base64 of UTF-16LE text, so no quoting can go wrong.
    private static String encode(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    private static String windowsPath(File folder) {
        try {
            return folder == null ? null : run(List.of("wslpath", "-w", folder.getPath())).trim();
        } catch (IOException e) {
            return null; // the dialog opens in its usual folder instead
        }
    }

    // Runs a command and returns what it printed, or throws if it failed.
    private static String run(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (InputStream out = process.getInputStream()) {
            String text = new String(out.readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IOException(command.get(0) + " failed with exit code " + process.exitValue());
            }
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + command.get(0), e);
        }
    }
}
