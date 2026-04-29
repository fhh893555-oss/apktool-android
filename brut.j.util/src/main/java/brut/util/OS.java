/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.util;

import brut.common.BrutException;
import brut.common.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class OS {
    private static final String TAG = "";

    /**
     * Process-wide override for the directory in which transient files and
     * directories are created. When {@code null}, {@link File#createTempFile}
     * uses {@code java.io.tmpdir}.
     *
     * On Android, {@code java.io.tmpdir} defaults to {@code /tmp} which does
     * not exist; the host must call this (or {@link brut.androlib.Config#setTempDirectory})
     * with an app-writable location before any decode/build call.
     */
    private static volatile File sDefaultTempDirectory;

    private OS() {
        // Private constructor for utility class.
    }

    public static void setDefaultTempDirectory(File dir) {
        if (dir != null) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        sDefaultTempDirectory = dir;
    }

    public static File getDefaultTempDirectory() {
        return sDefaultTempDirectory;
    }

    public static File createTempFile(String prefix, String suffix) throws IOException {
        File parent = sDefaultTempDirectory;
        if (parent != null) {
            return File.createTempFile(prefix, suffix, parent);
        }
        return File.createTempFile(prefix, suffix);
    }

    public static void mkdir(String dir) {
        mkdir(new File(dir));
    }

    public static void mkdir(File dir) {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    public static void rmfile(String file) {
        rmfile(new File(file));
    }

    public static void rmfile(File file) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public static void rmdir(String dir) {
        rmdir(new File(dir));
    }

    public static void rmdir(File dir) {
        if (!dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                rmdir(file);
            } else {
                rmfile(file);
            }
        }
        rmfile(dir);
    }

    public static void mvfile(String src, String dest) throws BrutException {
        mvfile(new File(src), new File(dest));
    }

    public static void mvfile(File src, File dest) throws BrutException {
        try {
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BrutException("Could not move file: " + src, ex);
        }
    }

    public static void cpfile(String src, String dest) throws BrutException {
        cpfile(new File(src), new File(dest));
    }

    public static void cpfile(File src, File dest) throws BrutException {
        if (!src.isFile()) {
            return;
        }

        try {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BrutException("Could not copy file: " + src, ex);
        }
    }

    public static void cpdir(String src, String dest) throws BrutException {
        cpdir(new File(src), new File(dest));
    }

    public static void cpdir(File src, File dest) throws BrutException {
        if (!src.isDirectory()) {
            return;
        }

        mkdir(dest);

        File[] files = src.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            File destFile = new File(dest, file.getName());
            if (file.isDirectory()) {
                cpdir(file, destFile);
            } else {
                cpfile(file, destFile);
            }
        }
    }

    public static void exec(String[] cmd) throws BrutException {
        try {
            ProcessBuilder builder = new ProcessBuilder(cmd);
            Process ps = builder.start();

            // Capture stderr into a bounded ring buffer in addition to logging.
            // On Android the StreamForwarder's Log.w() writes go to logcat,
            // which callers can't easily inspect. Surfacing the tail of stderr
            // directly in the BrutException message turns "exit code = 1"
            // failures into actionable diagnostics for any host.
            StreamForwarder errF = new StreamForwarder(ps.getErrorStream(), "ERROR", true);
            StreamForwarder outF = new StreamForwarder(ps.getInputStream(), "OUTPUT", false);
            errF.start();
            outF.start();

            int exitValue = ps.waitFor();
            // Best-effort: give forwarders a moment to drain after the child
            // has exited, otherwise the captured tail can be empty on fast
            // failures.
            errF.join(500);
            outF.join(500);
            if (exitValue != 0) {
                String tail = errF.captured();
                StringBuilder msg = new StringBuilder("Execution failed (exit code = ")
                    .append(exitValue).append("): ").append(Arrays.toString(cmd));
                if (!tail.isEmpty()) {
                    msg.append(System.lineSeparator()).append("stderr:").append(System.lineSeparator()).append(tail);
                }
                throw new BrutException(msg.toString());
            }
        } catch (IOException ex) {
            throw new BrutException("could not exec: " + Arrays.toString(cmd), ex);
        } catch (InterruptedException ex) {
            throw new BrutException("could not exec : " + Arrays.toString(cmd), ex);
        }
    }

    public static String execAndReturn(String[] cmd) {
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.redirectErrorStream(true);

            Process process = builder.start();
            StreamCollector collector = new StreamCollector(process.getInputStream());
            executor.execute(collector);
            process.waitFor(15, TimeUnit.SECONDS);
            executor.shutdownNow();

            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                Log.w(TAG, "Stream collector did not terminate.");
            }
            return collector.get();
        } catch (IOException | InterruptedException ignored) {
            return null;
        }
    }

    public static File createTempDirectory() throws BrutException {
        try {
            File tmp = createTempFile("BRUT", null);
            tmp.deleteOnExit();

            if (!tmp.delete()) {
                throw new BrutException("Could not delete tmp file: " + tmp.getAbsolutePath());
            }
            if (!tmp.mkdir()) {
                throw new BrutException("Could not create tmp dir: " + tmp.getAbsolutePath());
            }

            return tmp;
        } catch (IOException ex) {
            throw new BrutException("Could not create tmp dir", ex);
        }
    }

    private static class StreamForwarder extends Thread {
        // Cap capture to keep memory bounded if a child subprocess goes
        // chatty (e.g. aapt2 on a corrupt resource table can emit thousands
        // of lines). 16 KB is enough for the actual error pinpoint without
        // turning the exception into a wall of text.
        private static final int CAPTURE_LIMIT_BYTES = 16 * 1024;

        private final InputStream mIn;
        private final String mType;
        private final boolean mCapture;
        private final StringBuilder mCaptured;

        public StreamForwarder(InputStream in, String type) {
            this(in, type, false);
        }

        public StreamForwarder(InputStream in, String type, boolean capture) {
            mIn = in;
            mType = type;
            mCapture = capture;
            mCaptured = capture ? new StringBuilder() : null;
        }

        public String captured() {
            if (mCaptured == null) {
                return "";
            }
            synchronized (mCaptured) {
                return mCaptured.toString();
            }
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(mIn))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (mType.equals("OUTPUT")) {
                        Log.i(TAG, line);
                    } else {
                        Log.w(TAG, line);
                    }
                    if (mCapture) {
                        synchronized (mCaptured) {
                            if (mCaptured.length() < CAPTURE_LIMIT_BYTES) {
                                mCaptured.append(line).append(System.lineSeparator());
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static class StreamCollector implements Runnable {
        private final InputStream mIn;
        private final StringBuilder mBuffer;

        public StreamCollector(InputStream in) {
            mIn = in;
            mBuffer = new StringBuilder();
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(mIn))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    mBuffer.append(line).append('\n');
                }
            } catch (IOException ignored) {
            }
        }

        public String get() {
            return mBuffer.toString();
        }
    }
}
