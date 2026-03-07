package gg.modl.bridge.util;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Merges new default YAML keys from the JAR into user-edited external files,
 * preserving all user customizations, comments, and formatting.
 * <p>
 * Works by comparing parsed YAML maps to find missing keys at any nesting depth,
 * then extracting the raw text for those keys from the JAR default and inserting
 * them into the user's file at the correct location. This avoids rewriting the
 * entire file through SnakeYAML (which strips comments and alters quote styles).
 * <p>
 * Ported from the modl minecraft plugin's core module.
 */
public final class YamlMergeUtil {

    private static final String LOG_PREFIX = "[modl-bridge] ";

    private YamlMergeUtil() {}

    /**
     * Merge default values from a JAR resource into an external YAML file.
     * <ul>
     *   <li>If the external file doesn't exist, copies the JAR default as-is.</li>
     *   <li>If the external file exists, recursively finds keys present in the JAR
     *       default but missing from the user's file, extracts their raw YAML text
     *       (preserving comments and formatting), and inserts them at the correct
     *       position in the user's file.</li>
     * </ul>
     *
     * @param jarResourcePath classpath resource path (e.g. "/config.yml")
     * @param externalFile    path to the user's file on disk
     * @param logger          logger for status/warning messages
     */
    @SuppressWarnings("unchecked")
    public static void mergeWithDefaults(String jarResourcePath, Path externalFile, Logger logger) {
        try {
            if (!Files.exists(externalFile)) {
                copyResourceToFile(jarResourcePath, externalFile, logger);
                return;
            }

            String jarText = readJarResource(jarResourcePath);
            if (jarText == null) {
                logger.warning(LOG_PREFIX + "JAR resource not found: " + jarResourcePath);
                return;
            }
            jarText = normalizeLineEndings(jarText);

            Yaml yaml = new Yaml();

            Map<Object, Object> defaults;
            try {
                defaults = yaml.load(jarText);
            } catch (Exception e) {
                logger.warning(LOG_PREFIX + "Failed to parse JAR default " + jarResourcePath + ", skipping merge");
                return;
            }
            if (defaults == null) return;

            String userText = normalizeLineEndings(Files.readString(externalFile, StandardCharsets.UTF_8));

            Map<Object, Object> userValues;
            try {
                userValues = yaml.load(userText);
            } catch (Exception e) {
                logger.warning(LOG_PREFIX + "Failed to parse " + externalFile.getFileName() + ", skipping merge: " + e.getMessage());
                return;
            }
            if (userValues == null) userValues = new LinkedHashMap<>();

            List<String> missingPaths = new ArrayList<>();
            collectMissingPaths(defaults, userValues, "", missingPaths);
            if (missingPaths.isEmpty()) return;

            List<String> jarLines = Arrays.asList(jarText.split("\n", -1));
            List<String> userLines = new ArrayList<>(Arrays.asList(userText.split("\n", -1)));

            int inserted = 0;
            for (String path : missingPaths) {
                String rawSection = extractSectionByPath(jarLines, path);
                if (rawSection == null) continue;

                int insertAt = findInsertionPoint(userLines, path);
                if (insertAt < 0) continue;

                List<String> newLines = new ArrayList<>(Arrays.asList(rawSection.split("\n", -1)));
                while (!newLines.isEmpty() && newLines.get(newLines.size() - 1).isEmpty()) {
                    newLines.remove(newLines.size() - 1);
                }

                userLines.addAll(insertAt, newLines);
                inserted++;
            }

            if (inserted > 0) {
                Files.writeString(externalFile, String.join("\n", userLines), StandardCharsets.UTF_8);
                logger.info(LOG_PREFIX + "Merged " + inserted + " new section(s) into " + externalFile.getFileName());
            }

        } catch (IOException e) {
            logger.warning(LOG_PREFIX + "Failed to merge defaults for " + externalFile.getFileName() + ": " + e.getMessage());
        }
    }

    private static void copyResourceToFile(String jarResourcePath, Path externalFile, Logger logger) throws IOException {
        try (InputStream jarStream = YamlMergeUtil.class.getResourceAsStream(jarResourcePath)) {
            if (jarStream == null) {
                logger.warning(LOG_PREFIX + "JAR resource not found: " + jarResourcePath);
                return;
            }
            Files.createDirectories(externalFile.getParent());
            Files.copy(jarStream, externalFile);
        }
    }

    private static String readJarResource(String jarResourcePath) throws IOException {
        try (InputStream jarStream = YamlMergeUtil.class.getResourceAsStream(jarResourcePath)) {
            return jarStream != null ? new String(jarStream.readAllBytes(), StandardCharsets.UTF_8) : null;
        }
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * Recursively compare two maps and collect paths that exist in defaults but
     * not in userValues. Only collects the shallowest missing path — if an entire
     * subtree is missing, it collects the root of that subtree (not every leaf).
     */
    @SuppressWarnings("unchecked")
    private static void collectMissingPaths(Map<Object, Object> defaults, Map<Object, Object> userValues,
                                            String parentPath, List<String> missing) {
        for (Map.Entry<Object, Object> entry : defaults.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String fullPath = parentPath.isEmpty() ? key : parentPath + "." + key;

            if (!userValues.containsKey(entry.getKey())) {
                missing.add(fullPath);
            } else if (entry.getValue() instanceof Map && userValues.get(entry.getKey()) instanceof Map) {
                collectMissingPaths(
                        (Map<Object, Object>) entry.getValue(),
                        (Map<Object, Object>) userValues.get(entry.getKey()),
                        fullPath, missing
                );
            }
        }
    }

    /**
     * Navigate to a key by dot-separated path in the JAR lines and extract
     * its raw YAML section (the key line plus all indented children).
     */
    private static String extractSectionByPath(List<String> lines, String path) {
        String[] parts = path.split("\\.");
        int searchFrom = 0;
        int expectedIndent = 0;

        for (int p = 0; p < parts.length; p++) {
            int keyLine = findKeyLine(lines, searchFrom, expectedIndent, parts[p]);
            if (keyLine < 0) return null;

            if (p == parts.length - 1) {
                return extractSection(lines, keyLine, expectedIndent);
            }

            int childIndent = detectChildIndent(lines, keyLine, expectedIndent);
            if (childIndent < 0) return null;
            searchFrom = keyLine + 1;
            expectedIndent = childIndent;
        }
        return null;
    }

    /**
     * Find where to insert a missing path in the user's file.
     * For top-level keys, appends at the end.
     * For nested keys, inserts at the end of the parent section.
     */
    private static int findInsertionPoint(List<String> lines, String path) {
        String[] parts = path.split("\\.");

        if (parts.length == 1) {
            int end = lines.size();
            while (end > 0 && lines.get(end - 1).trim().isEmpty()) end--;
            return end;
        }

        int searchFrom = 0;
        int expectedIndent = 0;

        for (int p = 0; p < parts.length - 1; p++) {
            int keyLine = findKeyLine(lines, searchFrom, expectedIndent, parts[p]);
            if (keyLine < 0) return -1;

            int childIndent = detectChildIndent(lines, keyLine, expectedIndent);
            if (childIndent < 0) {
                return keyLine + 1;
            }

            if (p < parts.length - 2) {
                searchFrom = keyLine + 1;
                expectedIndent = childIndent;
            } else {
                return findSectionEnd(lines, keyLine, expectedIndent);
            }
        }
        return -1;
    }

    /**
     * Find the line number after the last content line of a section.
     */
    private static int findSectionEnd(List<String> lines, int sectionStart, int sectionIndent) {
        int lastContentLine = sectionStart;
        for (int i = sectionStart + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (isBlankOrComment(trimmed)) continue;
            if (getIndent(line) <= sectionIndent) break;
            lastContentLine = i;
        }
        return lastContentLine + 1;
    }

    /**
     * Find a key line at the expected indentation level, starting from a given line.
     */
    private static int findKeyLine(List<String> lines, int from, int expectedIndent, String key) {
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (isBlankOrComment(trimmed)) continue;

            int indent = getIndent(line);
            if (indent < expectedIndent) return -1;
            if (indent > expectedIndent) continue;

            if (isKeyMatch(trimmed, key)) return i;
        }
        return -1;
    }

    /**
     * Check if a trimmed YAML line defines the given key.
     * Handles plain keys, double-quoted keys, and single-quoted keys.
     */
    private static boolean isKeyMatch(String trimmedLine, String key) {
        return trimmedLine.equals(key + ":")
                || trimmedLine.startsWith(key + ": ")
                || trimmedLine.equals("\"" + key + "\":")
                || trimmedLine.startsWith("\"" + key + "\": ")
                || trimmedLine.equals("'" + key + "':")
                || trimmedLine.startsWith("'" + key + "': ");
    }

    /**
     * Detect the indentation of the first child line under a section key.
     */
    private static int detectChildIndent(List<String> lines, int parentLine, int parentIndent) {
        for (int i = parentLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (isBlankOrComment(trimmed)) continue;
            int indent = getIndent(line);
            if (indent <= parentIndent) return -1;
            return indent;
        }
        return -1;
    }

    /**
     * Extract the raw text of a YAML section: the key line and all deeper-indented children.
     */
    private static String extractSection(List<String> lines, int keyLine, int keyIndent) {
        int endLine = keyLine + 1;
        while (endLine < lines.size()) {
            String line = lines.get(endLine);
            String trimmed = line.trim();
            if (isBlankOrComment(trimmed)) {
                endLine++;
                continue;
            }
            if (getIndent(line) <= keyIndent) break;
            endLine++;
        }
        // Trim trailing blank lines from the section
        while (endLine > keyLine + 1 && lines.get(endLine - 1).trim().isEmpty()) {
            endLine--;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = keyLine; i < endLine; i++) {
            sb.append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static boolean isBlankOrComment(String trimmedLine) {
        return trimmedLine.isEmpty() || trimmedLine.charAt(0) == '#';
    }

    private static int getIndent(String line) {
        return line.length() - line.stripLeading().length();
    }
}
