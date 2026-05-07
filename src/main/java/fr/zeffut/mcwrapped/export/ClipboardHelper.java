package fr.zeffut.mcwrapped.export;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

public final class ClipboardHelper {

    private ClipboardHelper() {}

    public static void copyImage(final BufferedImage image) {
        final Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(new ImageTransferable(image), null);
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
