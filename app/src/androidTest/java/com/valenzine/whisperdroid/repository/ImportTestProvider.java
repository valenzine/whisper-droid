package com.valenzine.whisperdroid.repository;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** A real remote provider that deliberately ignores cancellation and interruption. */
public class ImportTestProvider extends ContentProvider {
    private static final AtomicInteger opened = new AtomicInteger();
    private static volatile boolean released;
    private static final List<ParcelFileDescriptor> writers = new ArrayList<>();
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "audio/wav"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        String path = uri.getLastPathSegment();
        if ("release".equals(path) || "reset".equals(path)) {
            released = true;
            synchronized (writers) {
                for (ParcelFileDescriptor writer : writers) try { writer.close(); } catch (IOException ignored) { }
                writers.clear();
            }
            if ("reset".equals(path)) { opened.set(0); released = false; }
        }
        if ("state".equals(path)) {
            MatrixCursor state = new MatrixCursor(new String[]{"opened"});
            state.addRow(new Object[]{opened.get()});
            return state;
        }
        if ("stall-query".equals(path)) {
            opened.incrementAndGet();
            while (!released) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) { }
            }
        }
        MatrixCursor metadata = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        metadata.addRow(new Object[]{path + ".wav", null});
        return metadata;
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        return openAssetFile(uri, mode, null);
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode, CancellationSignal signal) throws java.io.FileNotFoundException {
        try {
            if ("stall-read".equals(uri.getLastPathSegment())) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                synchronized (writers) { writers.add(pipe[1]); }
                opened.incrementAndGet();
                return new AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH);
            }
            int seconds = "five-minutes".equals(uri.getLastPathSegment()) ? 300 : 1;
            int bytes = 8000 * seconds * 2;
            File file = new File(getContext().getCacheDir(), "provider-" + uri.getLastPathSegment() + ".wav");
            try (FileOutputStream output = new FileOutputStream(file)) {
                ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
                header.put("RIFF".getBytes()).putInt(36 + bytes).put("WAVEfmt ".getBytes())
                    .putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000)
                    .putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(bytes);
                output.write(header.array());
                output.write(new byte[bytes]);
            }
            return new AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length());
        } catch (IOException error) {
            throw new java.io.FileNotFoundException(error.getMessage());
        }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
