package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTextArea;
import javax.swing.TransferHandler;
import org.junit.jupiter.api.Test;

class FileDropTest {

    @Test
    void aDroppedFileIsOpenedAndPastedTextStillGoesToTheEditor() {
        JTextArea editor = new JTextArea();
        List<File> opened = new ArrayList<>();
        FileDrop drop = new FileDrop(editor.getTransferHandler(), opened::add);
        editor.setTransferHandler(drop);

        File file = new File("BSTInsert.java");
        Transferable files = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {DataFlavor.javaFileListFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor.equals(DataFlavor.javaFileListFlavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return List.of(file);
            }
        };
        assertTrue(drop.importData(new TransferHandler.TransferSupport(editor, files)));
        assertEquals(List.of(file), opened);

        assertTrue(drop.importData(new TransferHandler.TransferSupport(editor, new StringSelection("int x = 1;"))));
        assertEquals("int x = 1;", editor.getText(), "text is pasted as before");
    }
}
