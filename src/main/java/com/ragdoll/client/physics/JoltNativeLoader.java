package com.ragdoll.client.physics;

import com.github.stephengold.joltjni.NativeLibraryLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class JoltNativeLoader {
    private static final String WINDOWS_X64_NATIVE =
            "/windows/x86-64/com/github/stephengold/joltjni.dll";
    private static final String ANDROID_ARM64_NATIVE =
            "/android/arm64-v8a/libjoltjni.so";
    private static boolean loaded;

    private JoltNativeLoader() {
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }

        String osName = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT);
        String osVersion = System.getProperty("os.version", "")
                .toLowerCase(java.util.Locale.ROOT);
        String architecture = System.getProperty("os.arch", "")
                .toLowerCase(java.util.Locale.ROOT);
        boolean android = isAndroidRuntime(osName, osVersion);
        String resource;
        String libraryName;
        if (osName.contains("windows") && isX64(architecture)) {
            resource = WINDOWS_X64_NATIVE;
            libraryName = "joltjni.dll";
        } else if (android && isArm64(architecture)) {
            resource = ANDROID_ARM64_NATIVE;
            libraryName = "libjoltjni.so";
        } else {
            throw new IllegalStateException("No bundled Jolt native library for "
                    + osName + "/" + architecture + (android ? " (Android)" : ""));
        }

        try (InputStream nativeStream = JoltNativeLoader.class.getResourceAsStream(resource)) {
            if (nativeStream == null) {
                throw new IOException("Missing packaged native library " + resource);
            }
            Path extractionDirectory = Files.createTempDirectory(
                    "ragdoll-jolt-");
            Path nativeLibrary = extractionDirectory.resolve(libraryName);
            Files.copy(
                    nativeStream,
                    nativeLibrary,
                    StandardCopyOption.REPLACE_EXISTING);
            nativeLibrary.toFile().deleteOnExit();
            extractionDirectory.toFile().deleteOnExit();
            if (!NativeLibraryLoader.loadLibrary(
                    nativeLibrary.toAbsolutePath().toString())) {
                throw new IllegalStateException("Unable to load joltjni native library");
            }
            loaded = true;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to extract or load joltjni", exception);
        }
    }

    static boolean isAndroidRuntime(String osName, String osVersion) {
        String normalizedOsName = osName.toLowerCase(java.util.Locale.ROOT);
        String normalizedOsVersion = osVersion.toLowerCase(java.util.Locale.ROOT);
        if (normalizedOsName.contains("android") || normalizedOsVersion.contains("android")) {
            return true;
        }
        try {
            Class.forName("android.os.Build", false, JoltNativeLoader.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean isX64(String architecture) {
        return architecture.equals("amd64") || architecture.equals("x86_64");
    }

    private static boolean isArm64(String architecture) {
        return architecture.equals("aarch64") || architecture.equals("arm64");
    }
}
