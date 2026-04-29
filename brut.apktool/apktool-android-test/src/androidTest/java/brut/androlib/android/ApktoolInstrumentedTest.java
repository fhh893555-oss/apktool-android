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
package brut.androlib.android;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import brut.androlib.ApkBuilder;
import brut.androlib.ApkDecoder;
import brut.androlib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * End-to-end instrumented tests for the Android port.
 *
 * <p>These run on a real ART runtime against the bundled
 * {@code libaapt2.so}. They cover the three blockers identified during the
 * port:</p>
 *
 * <ol>
 *   <li>{@link #aapt2VersionExecutes()} — does the bionic-static aapt2
 *       binary actually launch on a stock device (no Termux, no
 *       {@code libc++_shared.so})?</li>
 *   <li>{@link #decodeAndRebuild()} — does the full decode/build cycle
 *       round-trip a vanilla APK on Android, exercising the PNGJ 9-patch
 *       decoder and the commons-compress ZIP reader?</li>
 *   <li>{@link #featureFlagsBuild()} — does the bundled aapt2 understand
 *       the modern AOSP {@code --feature-flags} / sparse-encoded
 *       {@code resources.arsc} that current Play-Store APKs ship with?</li>
 * </ol>
 *
 * <p>Required test assets (place under {@code src/androidTest/assets/}):</p>
 * <ul>
 *   <li>{@code sample.apk} — small testapp APK; the existing
 *       {@code brut.apktool.apktool-lib} test fixture compiled to APK is
 *       sufficient.</li>
 *   <li>{@code modern.apk} — any current Play-Store APK with sparse-encoded
 *       resources / feature-flag metadata. Used only by
 *       {@link #featureFlagsBuild()}; the test is skipped if the asset is
 *       absent.</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class ApktoolInstrumentedTest {

    private Context context;
    private File workDir;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        workDir = new File(context.getCacheDir(), "apktool-test");
        if (workDir.exists()) {
            deleteRecursive(workDir);
        }
        if (!workDir.mkdirs()) {
            fail("Could not create work dir: " + workDir);
        }
    }

    @After
    public void tearDown() {
        if (workDir != null) {
            deleteRecursive(workDir);
        }
    }

    @Test
    public void aapt2VersionExecutes() throws Exception {
        File aaptBin = new File(context.getApplicationInfo().nativeLibraryDir, ApktoolAndroid.AAPT2_LIBRARY_NAME);
        assertTrue("libaapt2.so missing in nativeLibraryDir: " + aaptBin, aaptBin.exists());

        ProcessBuilder pb = new ProcessBuilder(aaptBin.getAbsolutePath(), "version");
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        int exit = p.waitFor();

        // aapt2 prints "Android Asset Packaging Tool (aapt) 2.x" on success.
        // We accept any exit code as long as the banner appears, since some
        // aapt2 builds exit non-zero when no input files are passed.
        String stdout = out.toString();
        assertTrue("aapt2 did not print expected banner. exit=" + exit + " out=\n" + stdout,
            stdout.contains("Android Asset Packaging Tool"));
    }

    @Test
    public void decodeAndRebuild() throws Exception {
        File sampleApk = copyAsset("sample.apk", new File(workDir, "sample.apk"));
        Config config = ApktoolAndroid.configure(context);

        File outDir = new File(workDir, "decoded");
        new ApkDecoder(sampleApk, config).decode(outDir);

        assertTrue("AndroidManifest.xml missing after decode",
            new File(outDir, "AndroidManifest.xml").isFile());

        File rebuiltApk = new File(workDir, "rebuilt.apk");
        new ApkBuilder(outDir, config).build(rebuiltApk);

        assertTrue("rebuilt.apk not produced", rebuiltApk.isFile());
        assertTrue("rebuilt.apk is empty", rebuiltApk.length() > 0);

        Set<String> entries = listZipEntries(rebuiltApk);
        assertTrue("rebuilt.apk missing AndroidManifest.xml: " + entries,
            entries.contains("AndroidManifest.xml"));
        assertTrue("rebuilt.apk missing resources.arsc: " + entries,
            entries.contains("resources.arsc"));
        // dex is optional depending on what the test apk actually contains;
        // assert at least one classes*.dex entry.
        boolean hasDex = false;
        for (String e : entries) {
            if (e.startsWith("classes") && e.endsWith(".dex")) {
                hasDex = true;
                break;
            }
        }
        assertTrue("rebuilt.apk missing classes*.dex: " + entries, hasDex);
    }

    @Test
    public void featureFlagsBuild() throws Exception {
        File modernApk;
        try {
            modernApk = copyAsset("modern.apk", new File(workDir, "modern.apk"));
        } catch (AssertionError missing) {
            // modern.apk is an optional fixture. Test is a no-op if absent.
            return;
        }

        Config config = ApktoolAndroid.configure(context);
        File outDir = new File(workDir, "modern-decoded");
        new ApkDecoder(modernApk, config).decode(outDir);

        File rebuiltApk = new File(workDir, "modern-rebuilt.apk");
        new ApkBuilder(outDir, config).build(rebuiltApk);

        assertTrue("modern rebuilt apk not produced", rebuiltApk.isFile());
        Set<String> entries = listZipEntries(rebuiltApk);
        assertTrue("modern rebuilt apk missing resources.arsc", entries.contains("resources.arsc"));
    }

    // -- helpers --------------------------------------------------------------

    private File copyAsset(String name, File dst) throws Exception {
        AssetManager assets = context.getAssets();
        InputStream in;
        try {
            in = assets.open(name);
        } catch (Exception ex) {
            throw new AssertionError("missing test asset: androidTest/assets/" + name, ex);
        }
        File parent = dst.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
        assertNotNull("copied asset is null", dst);
        assertTrue("copied asset missing", dst.exists());
        return dst;
    }

    private Set<String> listZipEntries(File apk) throws Exception {
        Set<String> entries = new LinkedHashSet<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.FileInputStream(apk))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                entries.add(e.getName());
                in.closeEntry();
            }
        }
        return entries;
    }

    private static void deleteRecursive(File root) {
        if (!root.exists()) {
            return;
        }
        File[] kids = root.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursive(k);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        root.delete();
    }

    @SuppressWarnings("unused") // kept for readability of the test parameter list
    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
