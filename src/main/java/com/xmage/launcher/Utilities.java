package com.xmage.launcher;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author BetaSteward
 */
public class Utilities {

    private static final String OS_name = System.getProperty("os.name").toLowerCase();
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Utilities.class);

    public enum OS {

        WIN,
        NIX,
        OSX,
        UNKNOWN
    }

    public static File getInstallPath() {
        File path = null;
        try {
            path = new File(Utilities.class.getProtectionDomain().getCodeSource().getLocation().toURI().getSchemeSpecificPart()).getParentFile();
        } catch (URISyntaxException ex) {
            logger.error("Error: ", ex);
        }
        return path;
    }

    public static OS getOS() {
        if (OS_name.contains("win")) {
            return OS.WIN;
        }
        if (OS_name.contains("mac")) {
            return OS.OSX;
        }
        if (OS_name.contains("nix") || OS_name.contains("nux")) {
            return OS.NIX;
        }
        return OS.UNKNOWN;
    }

    public static String getArch() {
        String arch = System.getenv("PROCESSOR_ARCHITECTURE");
        String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");

        return arch.endsWith("64") || wow64Arch != null && wow64Arch.endsWith("64") ? "64" : "32";
    }

    public static String getOSandArch() {
        String OS_arch = "windows-i586";
        switch (getOS()) {
            case WIN:
                OS_arch = "windows-" + (getArch().equals("64") ? "x64" : "i586");
                break;
            case OSX:
                OS_arch = "macosx-x64";
                break;
            case NIX:
                String arch = System.getProperty("os.arch");
                OS_arch = "linux-" + (arch.startsWith("i") ? "i586" : "x64"); // assume arch is same as jvm arch
                break;
        }
        return OS_arch;
    }

    public static JSONObject readJsonFromUrl(URL url) throws IOException, JSONException {
        // redirect support (example: from http to https)
        URLConnection connection = url.openConnection();
        String redirect = connection.getHeaderField("Location");
        if (redirect != null) {
            connection = new URL(redirect).openConnection();
        }

        // data read
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            return new JSONObject(readAll(rd));
        }
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public static Process launchClientProcess() {

        return launchProcess("mage.client.MageFrame",
                "",
                "mage-client",
                Config.getInstance().getClientJavaOpts());

    }

    public static Process launchServerProcess() {

        return launchProcess("mage.server.Main",
                Config.getInstance().isServerTestMode() ? "-testMode=true" : "",
                "mage-server",
                Config.getInstance().getServerJavaOpts());

    }

    public static void stopProcess(Process p) {
        p.destroy();
    }

    public static File getJavaHome() {
        return getJavaHome(false);
    }

    public static File getJavaHome(boolean useSystemJavaOnly) {
        File javaHome;
        if (useSystemJavaOnly || Config.getInstance().useSystemJava()) {
            // TODO: is it possible to take "system" java from a launcher running process?
            //  e.g. if you run launcher under java 8 then launcher will use same java bin path to run other apps
            javaHome = new File(System.getProperty("java.home"));
        } else if (getOS() == OS.OSX) {
            javaHome = new File(Utilities.getInstallPath(), "/java/jre" + Config.getInstance().getInstalledJavaVersion() + ".jre/Contents/Home");
        } else {
            javaHome = new File(Utilities.getInstallPath(), "/java/jre" + Config.getInstance().getInstalledJavaVersion());
        }
        return javaHome;
    }

    private static Process launchProcess(String main, String args, String path, String javaOpts) {
        File javaHome = getJavaHome();
        File javaBin = new File(javaHome, "/bin/java");
        File xmagePath = new File(Utilities.getInstallPath(), "/xmage/" + path);
        File classPath = new File(xmagePath, "/lib/*");

        if (!javaBin.getParentFile().exists() || !xmagePath.isDirectory()) {
            return null;
        }

        logger.info("Launching Process:");
        logger.info("Java bin: " + javaBin);
        logger.info("XMage Path: " + xmagePath);
        logger.info("Class Path: " + classPath);

        ArrayList<String> command = new ArrayList<>();
        command.add(javaBin.getAbsolutePath());
        command.addAll(Arrays.asList(javaOpts.split(" ")));
        command.add("-cp");
        command.add(classPath.getAbsolutePath());
        command.add(main);
        command.addAll(Arrays.asList(args.split(" ")));

        ProcessBuilder pb = new ProcessBuilder(command.toArray(new String[command.size()]));
        pb.environment().putAll(System.getenv());
        pb.environment().put("JAVA_HOME", javaHome.getAbsolutePath());
        pb.directory(xmagePath);
        pb.redirectErrorStream(true);
        try {
            return pb.start();
        } catch (IOException ex) {
            logger.error("Error staring process", ex);
        }
        return null;
    }

    public static void restart(File launcherJar) {
        // launcher must use default java
        File installPath = Utilities.getInstallPath();
        ArrayList<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(launcherJar.getPath());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(System.getenv());
        pb.directory(installPath);
        try {
            pb.start();
            System.exit(0);
        } catch (IOException ex) {
            logger.error("Error restarting launcher", ex);
        }
    }

}
