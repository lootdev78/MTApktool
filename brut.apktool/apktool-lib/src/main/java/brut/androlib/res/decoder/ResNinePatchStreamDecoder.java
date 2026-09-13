/* Android port: replaces desktop AWT/ImageIO with android.graphics. */
package brut.androlib.res.decoder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;

import brut.androlib.exceptions.AndrolibException;
import brut.androlib.exceptions.NinePatchNotFoundException;
import brut.androlib.res.data.LayoutBounds;
import brut.androlib.res.data.NinePatchData;
import brut.util.BinaryDataInputStream;
import org.apache.commons.io.IOUtils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;

public class ResNinePatchStreamDecoder implements ResStreamDecoder {
    @Override
    public void decode(InputStream in, OutputStream out) throws AndrolibException {
        Bitmap src = null;
        Bitmap dst = null;
        try {
            byte[] data = IOUtils.toByteArray(in);
            if (data.length == 0) return;

            src = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (src == null) throw new IOException("Invalid PNG image");
            int w = src.getWidth(), h = src.getHeight();
            dst = Bitmap.createBitmap(w + 2, h + 2, Bitmap.Config.ARGB_8888);
            new Canvas(dst).drawBitmap(src, 1, 1, null);

            NinePatchData np = findNinePatchData(data);
            drawHLine(dst, h + 1, np.paddingLeft + 1, w - np.paddingRight);
            drawVLine(dst, w + 1, np.paddingTop + 1, h - np.paddingBottom);

            int[] xDivs = np.xDivs;
            if (xDivs.length == 0) drawHLine(dst, 0, 1, w);
            else for (int i = 0; i < xDivs.length; i += 2) drawHLine(dst, 0, xDivs[i] + 1, xDivs[i + 1]);

            int[] yDivs = np.yDivs;
            if (yDivs.length == 0) drawVLine(dst, 0, 1, h);
            else for (int i = 0; i < yDivs.length; i += 2) drawVLine(dst, 0, yDivs[i] + 1, yDivs[i + 1]);

            try {
                LayoutBounds lb = findLayoutBounds(data);
                for (int i = 0; i < lb.left; i++) dst.setPixel(1 + i, h + 1, LayoutBounds.COLOR_TICK);
                for (int i = 0; i < lb.right; i++) dst.setPixel(w - i, h + 1, LayoutBounds.COLOR_TICK);
                for (int i = 0; i < lb.top; i++) dst.setPixel(w + 1, 1 + i, LayoutBounds.COLOR_TICK);
                for (int i = 0; i < lb.bottom; i++) dst.setPixel(w + 1, h - i, LayoutBounds.COLOR_TICK);
            } catch (NinePatchNotFoundException ignored) {
            }

            if (!dst.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("Could not encode PNG");
            }
        } catch (IOException | NullPointerException ex) {
            throw new AndrolibException(ex);
        } finally {
            if (src != null) src.recycle();
            if (dst != null) dst.recycle();
        }
    }

    private NinePatchData findNinePatchData(byte[] data) throws NinePatchNotFoundException, IOException {
        BinaryDataInputStream in = new BinaryDataInputStream(data, ByteOrder.BIG_ENDIAN);
        findChunk(in, NinePatchData.MAGIC);
        return NinePatchData.read(in);
    }

    private LayoutBounds findLayoutBounds(byte[] data) throws NinePatchNotFoundException, IOException {
        BinaryDataInputStream in = new BinaryDataInputStream(data, ByteOrder.BIG_ENDIAN);
        findChunk(in, LayoutBounds.MAGIC);
        return LayoutBounds.read(in);
    }

    private void findChunk(BinaryDataInputStream in, int magic) throws NinePatchNotFoundException, IOException {
        in.skipBytes(8);
        for (;;) {
            int size;
            try { size = in.readInt(); } catch (EOFException ignored) { throw new NinePatchNotFoundException(); }
            if (in.readInt() == magic) return;
            in.skipBytes(size + 4);
        }
    }

    private void drawHLine(Bitmap im, int y, int x1, int x2) {
        for (int x = x1; x <= x2; x++) im.setPixel(x, y, NinePatchData.COLOR_TICK);
    }

    private void drawVLine(Bitmap im, int x, int y1, int y2) {
        for (int y = y1; y <= y2; y++) im.setPixel(x, y, NinePatchData.COLOR_TICK);
    }
}
