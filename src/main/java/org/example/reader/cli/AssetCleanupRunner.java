package org.example.reader.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class AssetCleanupRunner {

    private static final String APPLY_FLAG = "--apply";
    private static final String AUDIO_FLAG = "--audio";
    private static final String AUDIO_ROOT_FLAG = "--audio-root-cache";
    private static final String HELP_FLAG = "--help";

    private static final int SAMPLE_LIMIT = 20;

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains(HELP_FLAG)) {
            printUsage();
            return;
        }

        boolean apply = arguments.contains(APPLY_FLAG);
        boolean includeAudio = arguments.contains(AUDIO_FLAG);
        boolean includeAudioRoot = arguments.contains(AUDIO_ROOT_FLAG);

        Properties properties = loadProperties();
        String dbUrl = properties.getProperty("spring.datasource.url", "jdbc:h2:file:./data/library");
        String dbUser = properties.getProperty("spring.datasource.username", "sa");
        String dbPassword = properties.getProperty("spring.datasource.password", "");

        Path illustrationDir = Path.of(properties.getProperty("illustration.cache-dir", "./data/illustrations"));
        Path portraitDir = Path.of(properties.getProperty("character.portrait.cache-dir", "./data/character-portraits"));
        Path audioDir = Path.of(properties.getProperty("tts.cache-dir", "./data/audio"));

        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            Set<String> illustrationFiles = queryFilenames(connection, "select image_filename from illustrations where image_filename is not null");
            Set<String> portraitFiles = queryFilenames(connection, "select portrait_filename from characters where portrait_filename is not null");
            Set<String> bookIds = includeAudio ? queryFilenames(connection, "select id from books") : Set.of();
            Set<String> chapterIds = includeAudio ? queryFilenames(connection, "select id from chapters") : Set.of();

            List<Path> orphanIllustrations = findOrphanedFiles(illustrationDir, illustrationFiles);
            List<Path> orphanPortraits = findOrphanedFiles(portraitDir, portraitFiles);
            List<Path> orphanAudio = includeAudio
                ? findOrphanedAudio(audioDir, bookIds, chapterIds, includeAudioRoot)
                : List.of();

            report("illustrations", orphanIllustrations);
            report("character portraits", orphanPortraits);
            if (includeAudio) {
                report("audio cache", orphanAudio);
            } else {
                System.out.println("Audio cleanup skipped (add --audio to enable).");
            }

            if (!apply) {
                System.out.println("Dry run only. Re-run with --apply to delete files.");
                return;
            }

            int deleted = 0;
            deleted += deletePaths(orphanIllustrations);
            deleted += deletePaths(orphanPortraits);
            deleted += deletePaths(orphanAudio);

            if (includeAudio) {
                deleted += deleteEmptyDirs(audioDir);
            }

            System.out.println("Deleted " + deleted + " paths.");
        } catch (Exception e) {
            System.err.println("Asset cleanup failed: " + e.getMessage());
        }
    }

    private static void printUsage() {
        System.out.println("Usage: AssetCleanupRunner [--apply] [--audio] [--audio-root-cache]");
        System.out.println("  --apply             Delete orphaned files (default is dry run).");
        System.out.println("  --audio             Also check TTS audio cache for missing books/chapters.");
        System.out.println("  --audio-root-cache  Include audio files stored directly under the audio cache dir.");
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = AssetCleanupRunner.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
                return properties;
            }
        } catch (IOException e) {
            System.err.println("Failed to load application.properties from classpath: " + e.getMessage());
        }
        Path fallbackPath = Path.of("src/main/resources/application.properties");
        if (Files.exists(fallbackPath)) {
            try (InputStream input = Files.newInputStream(fallbackPath)) {
                properties.load(input);
            } catch (IOException e) {
                System.err.println("Failed to load application.properties from disk: " + e.getMessage());
            }
        }
        return properties;
    }

    private static Set<String> queryFilenames(Connection connection, String sql) throws Exception {
        Set<String> values = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String value = resultSet.getString(1);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static List<Path> findOrphanedFiles(Path directory, Set<String> referencedFilenames) throws IOException {
        List<Path> orphaned = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return orphaned;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String filename = path.getFileName().toString();
                if (!referencedFilenames.contains(filename)) {
                    orphaned.add(path);
                }
            }
        }
        return orphaned;
    }

    private static List<Path> findOrphanedAudio(Path audioDir, Set<String> bookIds, Set<String> chapterIds,
                                                boolean includeRootCache) throws IOException {
        List<Path> orphaned = new ArrayList<>();
        if (!Files.isDirectory(audioDir)) {
            return orphaned;
        }

        try (DirectoryStream<Path> bookDirs = Files.newDirectoryStream(audioDir)) {
            for (Path bookDir : bookDirs) {
                if (Files.isRegularFile(bookDir)) {
                    if (includeRootCache) {
                        orphaned.add(bookDir);
                    }
                    continue;
                }
                if (!Files.isDirectory(bookDir)) {
                    continue;
                }
                String bookId = bookDir.getFileName().toString();
                if (!bookIds.contains(bookId)) {
                    orphaned.add(bookDir);
                    continue;
                }
                try (DirectoryStream<Path> voiceDirs = Files.newDirectoryStream(bookDir)) {
                    for (Path voiceDir : voiceDirs) {
                        if (!Files.isDirectory(voiceDir)) {
                            orphaned.add(voiceDir);
                            continue;
                        }
                        try (DirectoryStream<Path> chapterDirs = Files.newDirectoryStream(voiceDir)) {
                            for (Path chapterDir : chapterDirs) {
                                if (!Files.isDirectory(chapterDir)) {
                                    orphaned.add(chapterDir);
                                    continue;
                                }
                                String chapterId = chapterDir.getFileName().toString();
                                if (!chapterIds.contains(chapterId)) {
                                    orphaned.add(chapterDir);
                                }
                            }
                        }
                    }
                }
            }
        }

        return orphaned;
    }

    private static void report(String label, List<Path> orphaned) {
        System.out.println("Found " + orphaned.size() + " orphaned " + label + ".");
        int shown = 0;
        for (Path path : orphaned) {
            System.out.println("  " + path);
            shown++;
            if (shown >= SAMPLE_LIMIT && orphaned.size() > SAMPLE_LIMIT) {
                System.out.println("  ...");
                break;
            }
        }
    }

    private static int deletePaths(List<Path> paths) {
        int deleted = 0;
        for (Path path : paths) {
            try {
                if (Files.isDirectory(path)) {
                    deleted += deleteDirectory(path);
                } else if (Files.exists(path)) {
                    Files.delete(path);
                    deleted += 1;
                }
            } catch (IOException e) {
                System.err.println("Failed to delete " + path + ": " + e.getMessage());
            }
        }
        return deleted;
    }

    private static int deleteDirectory(Path directory) throws IOException {
        int deleted = 0;
        try (var walk = Files.walk(directory)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                if (Files.exists(path)) {
                    Files.delete(path);
                    deleted += 1;
                }
            }
        }
        return deleted;
    }

    private static int deleteEmptyDirs(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int deleted = 0;
        try (var walk = Files.walk(root)) {
            List<Path> dirs = walk.filter(Files::isDirectory)
                .sorted(Comparator.reverseOrder())
                .toList();
            for (Path dir : dirs) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    if (!stream.iterator().hasNext()) {
                        Files.delete(dir);
                        deleted += 1;
                    }
                }
            }
        }
        return deleted;
    }
}
