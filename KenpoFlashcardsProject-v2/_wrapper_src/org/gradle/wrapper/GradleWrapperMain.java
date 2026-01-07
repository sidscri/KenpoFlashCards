package org.gradle.wrapper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GradleWrapperMain {

    public static void main(String[] args) throws Exception {
        File projectDir = new File(System.getProperty("user.dir"));
        File propsFile = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties");
        if (!propsFile.exists()) {
            System.err.println("Missing gradle-wrapper.properties at: " + propsFile.getAbsolutePath());
            System.exit(1);
        }

        Properties p = new Properties();
        try (InputStream in = new FileInputStream(propsFile)) {
            p.load(in);
        }

        String distUrl = p.getProperty("distributionUrl");
        if (distUrl == null || distUrl.trim().isEmpty()) {
            System.err.println("distributionUrl is missing in gradle-wrapper.properties");
            System.exit(1);
        }
        distUrl = distUrl.trim();

        File gradleUserHome = resolveGradleUserHome();
        File distsDir = new File(gradleUserHome, "wrapper/dists");
        if (!distsDir.exists() && !distsDir.mkdirs()) {
            System.err.println("Failed to create Gradle dists dir: " + distsDir.getAbsolutePath());
            System.exit(1);
        }

        String distFileName = distUrl.substring(distUrl.lastIndexOf('/') + 1);
        String distBaseName = distFileName.replaceAll("\\.zip$", "");
        String urlHash = shortSha256(distUrl);

        File distParent = new File(new File(distsDir, distBaseName), urlHash);
        File distZip = new File(distParent, distFileName);
        File distUnpackMarker = new File(distParent, ".unpacked");

        if (!distParent.exists() && !distParent.mkdirs()) {
            System.err.println("Failed to create distribution dir: " + distParent.getAbsolutePath());
            System.exit(1);
        }

        if (!distZip.exists()) {
            System.out.println("Downloading Gradle: " + distUrl);
            download(distUrl, distZip);
        }

        if (!distUnpackMarker.exists()) {
            System.out.println("Unpacking Gradle...");
            unzip(distZip, distParent);
            try (FileOutputStream fos = new FileOutputStream(distUnpackMarker)) {
                fos.write("ok".getBytes("UTF-8"));
            }
        }

        File gradleHome = findGradleHome(distParent);
        if (gradleHome == null) {
            System.err.println("Could not locate Gradle home inside: " + distParent.getAbsolutePath());
            System.exit(1);
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        File gradleExec = new File(gradleHome, isWindows ? "bin/gradle.bat" : "bin/gradle");
        if (!gradleExec.exists()) {
            System.err.println("Gradle executable not found: " + gradleExec.getAbsolutePath());
            System.exit(1);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(gradleExec.getAbsolutePath());
        cmd.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(projectDir);
        pb.inheritIO();

        // Ensure JAVA_HOME is passed through if set, and set GRADLE_USER_HOME to keep caches consistent.
        Map<String, String> env = pb.environment();
        env.putIfAbsent("GRADLE_USER_HOME", gradleUserHome.getAbsolutePath());

        Process proc = pb.start();
        int code = proc.waitFor();
        System.exit(code);
    }

    private static File resolveGradleUserHome() {
        String env = System.getenv("GRADLE_USER_HOME");
        if (env != null && !env.trim().isEmpty()) return new File(env.trim());
        String userHome = System.getProperty("user.home");
        return new File(userHome, ".gradle");
    }

    private static String shortSha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] dig = md.digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8 && i < dig.length; i++) sb.append(String.format("%02x", dig[i]));
        return sb.toString();
    }

    private static void download(String urlStr, File outFile) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        if (code >= 300 && code < 400) {
            String loc = conn.getHeaderField("Location");
            if (loc != null) {
                download(loc, outFile);
                return;
            }
        }
        if (code != 200) {
            throw new IOException("Download failed: HTTP " + code);
        }
        try (InputStream in = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) fos.write(buf, 0, r);
        }
    }

    private static void unzip(File zipFile, File destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                File out = new File(destDir, e.getName());
                if (e.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) throw new IOException("Failed mkdir: " + out);
                } else {
                    File parent = out.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) throw new IOException("Failed mkdir: " + parent);
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = zis.read(buf)) != -1) fos.write(buf, 0, r);
                    }
                }
            }
        }
    }

    private static File findGradleHome(File distParent) {
        File[] kids = distParent.listFiles();
        if (kids == null) return null;
        for (File k : kids) {
            if (k.isDirectory() && new File(k, "bin").exists()) {
                return k;
            }
        }
        return null;
    }
}
