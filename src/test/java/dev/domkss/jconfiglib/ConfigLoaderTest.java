/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2025 Dominik Kiss
 * Repository: https://github.com/domkss/JConfigLib
 */

package dev.domkss.jconfiglib;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigLoaderTest {

    private static final String TEST_CONFIG_PATH = "build/test-config.yaml";
    private static final Logger LOGGER = Logger.getLogger(ConfigLoaderTest.class.getName());

    public static class TestConfig {
        @ConfigField
        public String name = "defaultName";

        @ConfigField(comment = "Test Comment with &^^some spice#:% and text")
        public int port = 1234;

        @ConfigField(comment = "Double value of -PI")
        public double PI = Math.PI * -1;

        @ConfigField(comment = "Float value of E")
        public float e = (float) Math.E;

        @ConfigField
        public boolean debug = true;
    }

    @BeforeEach
    @AfterEach
    public void cleanUp() {
        LOGGER.setLevel(Level.OFF);

        File file = new File(TEST_CONFIG_PATH);
        if (file.exists()) {
            assertTrue(file.delete(), " Failed to delete file:" + TEST_CONFIG_PATH);
        }
    }


    @Test
    @DisplayName("Creates config file if it doesn't exist using default field values")
    public void createsConfigFileIfMissing() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists(), "Config file exists before the test");


        //Create the file, check values and file existence
        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH, LOGGER);
        TestConfig default_config = configLoader.loadConfig(TestConfig.class);
        assertEquals("defaultName", default_config.name);
        assertEquals(1234, default_config.port);
        assertEquals(Math.PI * -1, default_config.PI);
        assertEquals((float) Math.E, default_config.e);
        assertTrue(default_config.debug);

        assertTrue(new File(TEST_CONFIG_PATH).exists());


        //Read the config from the file and check variables
        ConfigLoader configLoader2 = new ConfigLoader(TEST_CONFIG_PATH, LOGGER);
        TestConfig config_from_file = configLoader2.loadConfig(TestConfig.class);
        assertEquals("defaultName", config_from_file.name, "Config field 'name' loaded from the file should be the default value after read");
        assertEquals(1234, config_from_file.port, "Config field 'name' loaded from the file should be the default value after read");
        assertEquals(Math.PI * -1, default_config.PI);
        assertEquals((float) Math.E, default_config.e);
        assertTrue(default_config.debug);
    }

    @Test
    @DisplayName("Creates config file manually then reads its content to the config class")
    public void loadsConfigFromManualFile() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists(), "Config file exists before the test");
        // Create config file manually
        String YAML = "name: newName\n#Ignored@#Comment\nport : 9876\ndebug: false\nPI : 4.121121\ne: 3.12";
        Files.writeString(new File(TEST_CONFIG_PATH).toPath(), YAML);

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH, LOGGER);
        TestConfig config = configLoader.loadConfig(TestConfig.class);

        assertEquals("newName", config.name);
        assertEquals(9876, config.port);
        assertEquals(4.121121, config.PI);
        assertEquals(3.12f, config.e);
        assertFalse(config.debug);
    }

    @Test
    @DisplayName("Internal change of the config fields should not be saved to the config file")
    public void doesNotChangeFileIfUnchanged() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists(), "Config file exists before the test");

        TestConfig original = new TestConfig();
        original.name = "unchanged";
        original.port = 5555;

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH, LOGGER);
        TestConfig default_config = configLoader.loadConfig(TestConfig.class);
        String original_content = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        assertEquals("defaultName", default_config.name);
        assertEquals(1234, default_config.port);

        TestConfig reloaded_config = configLoader.loadConfig(TestConfig.class);
        String after_reload = Files.readString(new File(TEST_CONFIG_PATH).toPath());

        assertEquals(original_content, after_reload);
        assertNotEquals(original.name, reloaded_config.name);
        assertNotEquals(original.port, reloaded_config.port);
        assertEquals(original.PI, default_config.PI);
        assertEquals(original.e, default_config.e);
        assertEquals(original.debug, default_config.debug);

    }

    @Test
    @DisplayName("Checks if comments from the config class are preserved in the YAML config file")
    public void checkCommentAnnotationCorrectnessInYaml() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists(), "Config file exists before the test");

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH, LOGGER);
        TestConfig config = configLoader.loadConfig(TestConfig.class);

        File file = new File(TEST_CONFIG_PATH);
        assertTrue(file.exists(), "YAML file should be created when it doesn't exist");

        String yamlContent = Files.readString(new File(TEST_CONFIG_PATH).toPath());

        // Check if the comments from the annotations are available in the file
        assertTrue(yamlContent.contains("# Test Comment with &^^some spice#:% and text\nport:"),
                "The YAML file should preserve comments");

        assertTrue(yamlContent.contains("# Double value of -PI\nPI:"),
                "The YAML file should preserve comments");

        assertTrue(yamlContent.contains("# Float value of E\ne:"),
                "The YAML file should preserve comments");

    }

    @Test
    @DisplayName("Checks inline comments behaviour in the YAML config file")
    public void checkInlineCommentBehaviourInYaml() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists(), "Config file exists before the test");

        // Create config file manually
        String YAML = "name: newName #Ignored@#Comment\nport : 9877";
        Files.writeString(new File(TEST_CONFIG_PATH).toPath(), YAML);


        File file = new File(TEST_CONFIG_PATH);
        assertTrue(file.exists(), "YAML file should be created when it doesn't exist");

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH, LOGGER);
        TestConfig config = configLoader.loadConfig(TestConfig.class);


        String yamlContent = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        // Check that a field-level inline comment (after the 'name' field) is preserved
        assertFalse(yamlContent.contains("Ignored@#Comment"),
                "Inline comment preservation is not supported");

        //The reading should be not affected
        assertEquals("newName", config.name);
        assertEquals(9877, config.port);
        assertEquals(Math.PI * -1, config.PI);
        assertEquals((float) Math.E, config.e);
        assertTrue(config.debug);

    }


    @Test
    @DisplayName("Checks manual comment behaviour in the YAML config file")
    public void checkExternalCommentBehaviourInYaml() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists(), "Config file exists before the test");

        // Create config file manually
        String YAML = "#Ignored@#Comment\nname: newName\nport : 9877";
        Files.writeString(new File(TEST_CONFIG_PATH).toPath(), YAML);

        File file = new File(TEST_CONFIG_PATH);
        assertTrue(file.exists(), "YAML file should exist at this point");

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH, LOGGER);
        TestConfig config = configLoader.loadConfig(TestConfig.class);


        String yamlContent = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        // Check that a field-level inline comment (after the 'name' field) is preserved
        assertTrue(yamlContent.contains("# Ignored@#Comment\n"),
                "Manual comments should be preserved");

        //The reading should be not affected
        assertEquals("newName", config.name);
        assertEquals(9877, config.port);
        assertEquals(Math.PI * -1, config.PI);
        assertEquals((float) Math.E, config.e);
        assertTrue(config.debug);

    }


    @Test
    @DisplayName("Check content preservation of fields and comments unknown to the ConfigClass")
    public void checkExternalFieldAndCommentPreservation() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists(), "Config file exists before the test");

        //Create the default config file
        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH, LOGGER);
        TestConfig config = configLoader.loadConfig(TestConfig.class);

        File file = new File(TEST_CONFIG_PATH);
        assertTrue(file.exists(), "YAML file should be created when it doesn't exist");


        //Read and modify config file content
        String fileContent = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        fileContent = fileContent.replace("# Float value of E\ne: 2.7182817\ndebug: true", "");

        String extendedYamlString = fileContent + "\n#TestCommentForParam" +
                "\nexternalVar: 14" +
                "\notherField: 23.0" +
                "\n#CommentNumber2" +
                "\nlastUnknownField: Text\n" +
                "# Float value of E\ne: 2.7182817\ndebug: true";
        Files.writeString(new File(TEST_CONFIG_PATH).toPath(), extendedYamlString);


        //Reload config class from the modified file and check content
        TestConfig config_reload = configLoader.loadConfig(TestConfig.class);
        assertEquals(config.name, config_reload.name);
        assertEquals(config.PI, config_reload.PI);
        assertEquals(config.debug, config_reload.debug);
        assertEquals(config.e, config_reload.e);
        assertEquals(config.port, config_reload.port);

        //The reading should be not affected
        assertEquals("defaultName", config_reload.name);
        assertEquals(1234, config_reload.port);
        assertEquals(Math.PI * -1, config_reload.PI);
        assertEquals((float) Math.E, config_reload.e);
        assertTrue(config_reload.debug);


        String finalContent = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        // Check if the external comment lines and key value pairs are preserved in the file
        assertTrue(finalContent.contains("\n# TestCommentForParam\nexternalVar: 14\notherField: 23.0\n# CommentNumber2\nlastUnknownField: Text"),
                "External comments and key value pairs should be reserved");

    }

    @Test
    @DisplayName("Check invalid field data handling")
    public void invalidFieldDataHandling() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists(), "Config file exists before the test");

        Logger logger = Logger.getLogger(ConfigLoaderTest.class.getName());
        TestLogHandler testHandler = new TestLogHandler();
        logger.addHandler(testHandler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.INFO);

        //Create the file, check values and file existence
        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH, logger);
        TestConfig default_config = configLoader.loadConfig(TestConfig.class);
        assertEquals(1234, default_config.port);
        assertTrue(new File(TEST_CONFIG_PATH).exists());

        String fileContent = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        fileContent = fileContent.replace("1234", "NotANumber");
        Files.writeString(new File(TEST_CONFIG_PATH).toPath(), fileContent);

        boolean exceptionThrown = false;
        try {
            TestConfig reloaded_config = configLoader.loadConfig(TestConfig.class);
        } catch (ConfigLoader.InvalidConfigurationException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown, "For invalid type the lib has to throw an exception");


        boolean messageLogged = testHandler.getMessages().stream()
                .anyMatch(msg -> msg.contains("Error setting field value for port"));

        assertTrue(messageLogged, "Expected log message not found.");

        //File content unchanged
        String reloadedFileContent = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        assertTrue(reloadedFileContent.contains("port: NotANumber"));


    }

    @Test
    @DisplayName("Internal change of the config fields should be saved if the saveConfig function is called")
    public void currentFieldValuesAreCorrectlySaved() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists(), "Config file exists before the test");

        TestConfig original = new TestConfig();
        original.name = "changed";
        original.port = 5555;
        original.e = 0.1324f;
        original.PI = 1.12;
        original.debug = false;

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH, LOGGER);
        TestConfig default_config = configLoader.loadConfig(TestConfig.class);
        String original_content = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        assertEquals("defaultName", default_config.name);
        assertEquals(1234, default_config.port);
        assertEquals(Math.PI * -1, default_config.PI);
        assertEquals((float)Math.E, default_config.e);
        assertTrue(default_config.debug);

        //Save the field changes
        configLoader.saveConfig(original);

        TestConfig reloaded_config = configLoader.loadConfig(TestConfig.class);
        String after_reload = Files.readString(new File(TEST_CONFIG_PATH).toPath());

        assertEquals(original.name, reloaded_config.name);
        assertEquals(original.port, reloaded_config.port);
        assertEquals(original.e, reloaded_config.e);
        assertEquals(original.PI, reloaded_config.PI);
        assertEquals(original.debug, reloaded_config.debug);


        List<String> originalContentLines = Arrays.stream(original_content.split("\n")).toList();
        List<String> reloadedContentLines = List.of(after_reload.split("\n"));

        assertEquals(originalContentLines.size(), reloadedContentLines.size());
        long numberOfMatchingLines = IntStream.range(0,reloadedContentLines.size())
                .map(i -> originalContentLines.get(i).equals(reloadedContentLines.get(i)) ? 1 : 0)
                .sum();

        assertEquals(reloadedContentLines.size() - 5, numberOfMatchingLines);


    }

    // Custom log handler to capture log records
    static class TestLogHandler extends Handler {
        private final java.util.List<String> messages = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getLevel() + ": " + record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        public java.util.List<String> getMessages() {
            return messages;
        }
    }

}
