package org.intel.openvino;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A utility class to load the OpenVINO native libraries required for the
 * OpenVINO Runtime API.
 */
public final class NativeLibrary {

    public static final String NATIVE_LIBRARY_NAME = "inference_engine_java_api";
    private static final Logger logger = Logger.getLogger(NativeLibrary.class.getName());

    static {
        try {
            System.loadLibrary(NATIVE_LIBRARY_NAME);
        } catch (UnsatisfiedLinkError e) {
            try {
                loadNativeLibs();
            } catch (Exception ex) {
                logger.warning("Failed to load OpenVINO native libraries");
                throw ex;
            }
        }
    }

    private static Optional<File> getLibraryFilePath(String name, Map<String, File> mapOfFiles) {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            name = name + ".dll";
        } else if (osName.contains("mac")) {
            name = "lib" + name + ".dylib";
        } else {
            name = "lib" + name + ".so";
        }
        final String libFile = name;
        Optional<String> findFirst = mapOfFiles.keySet().stream().filter((t) -> t.startsWith(libFile)).sorted().findFirst();
        if (findFirst.isPresent()) {
            return Optional.of(mapOfFiles.get(findFirst.get()));
        }
        return Optional.empty();
    }

    /**
     * Use this method to initialize native libraries. Other files like
     * plugins.xml and *.mvcmd will be also copied to temporal location which
     * makes them visible in runtime. To Do: fix for Android!
     */
    public static void loadNativeLibs() {
        // A set of required libraries which are listed in dependency order.
        String nativeLibsStr = System.getProperty("org.intel.openvino.nativeLibs", "tbb tbbmalloc openvino inference_engine_java_api");

        String[] nativeLibs = nativeLibsStr.split("\\s+");

        Map<String, File> mapOfFiles = new HashMap<>();

        InputStream resources_list = null;
        try {
            // Get a list of all native resources (libraries, plugins and other files).
            resources_list
                    = NativeLibrary.class.getClassLoader().getResourceAsStream("resources_list.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(resources_list));

            // Create a temporal folder to unpack native files.
            File tmpDir = Files.createTempDirectory("openvino-native").toFile();
            tmpDir.deleteOnExit();

            String file;
            while ((file = r.readLine()) != null) {
                // Check that file name is valid
                if (!file.chars()
                        .allMatch(
                                c
                                -> Character.isLetterOrDigit(c)
                                || c == '.'
                                || c == '_'
                                || c == '-')) {
                    throw new IOException("Invalid file path: " + file);
                }

                URL url = NativeLibrary.class.getClassLoader().getResource(file);
                if (url == null) {
                    logger.warning("Resource not found: " + file);
                    continue;
                }
                File nativeLibTmpFile = new File(tmpDir, file);
                nativeLibTmpFile.deleteOnExit();
                try (InputStream in = url.openStream()) {
                    Files.copy(in, nativeLibTmpFile.toPath());
                }
                mapOfFiles.put(file, nativeLibTmpFile);
            }

            // Load native libraries.
            for (String lib : nativeLibs) {
                logger.log(Level.INFO, "Load of lib {0}", lib);
                Optional<File> libraryFilePath = getLibraryFilePath(lib, mapOfFiles);
                if (libraryFilePath.isPresent()) {
                    logger.log(Level.INFO, "Load lib {0} from path {1}", new Object[]{lib, libraryFilePath.get()});
                    try {
                        System.load(libraryFilePath.get().getAbsolutePath());
                    } catch (UnsatisfiedLinkError ex) {
                        logger.log(Level.WARNING, "Failed to load library " + file + ": " + ex.getMessage(), ex);
                    }
                } else {
                    logger.log(Level.WARNING, "Load lib {0} skiped! No file found", lib);
                }
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to load native Inference Engine libraries: " + ex.getMessage(), ex);
        } finally {
            if (resources_list != null) {
                try {
                    resources_list.close();
                } catch (IOException ex) {
                    logger.warning("Failed to read native libraries list");
                }
            }
        }
    }
}
