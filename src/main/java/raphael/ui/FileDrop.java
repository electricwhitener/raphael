package raphael.ui;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.InputEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

// Lets a file be dropped onto the window to open it, the way a file can be dropped onto a browser.
// The editor already has a TransferHandler, which does copy, cut and paste. This one wraps it:
// it takes dropped files itself and hands everything else to the editor's own, so pasting still
// works exactly as before. For the rest of the window there is nothing to wrap (inner is null).
// OOP: inheritance (extends TransferHandler) and overriding; composition (it holds the handler it
// wraps, and passes calls on to it: the Decorator pattern).
public class FileDrop extends TransferHandler {

    private final TransferHandler inner;
    private final Consumer<File> onFile;

    public FileDrop(TransferHandler inner, Consumer<File> onFile) {
        this.inner = inner;
        this.onFile = onFile;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return true;
        }
        return inner != null && inner.canImport(support);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            try {
                List<?> files = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty() && files.get(0) instanceof File) {
                    onFile.accept((File) files.get(0));
                    return true;
                }
            } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException e) {
                return false; // the drop went away before it could be read
            }
            return false;
        }
        return inner != null && inner.importData(support);
    }

    // Copying, cutting and dragging text are the inner handler's job.
    @Override
    public int getSourceActions(JComponent c) {
        return inner == null ? NONE : inner.getSourceActions(c);
    }

    @Override
    public void exportToClipboard(JComponent c, Clipboard clipboard, int action) {
        if (inner != null) {
            inner.exportToClipboard(c, clipboard, action);
        }
    }

    @Override
    public void exportAsDrag(JComponent c, InputEvent e, int action) {
        if (inner != null) {
            inner.exportAsDrag(c, e, action);
        }
    }
}
