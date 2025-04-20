package dev.domkss.jconfiglib;


import org.junit.jupiter.api.*;
import java.io.File;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        public float e =  (float)Math.E;

        @ConfigField
        public boolean debug = true;
    }

    @BeforeAll
    static void muteLogger() {
        LOGGER.setLevel(Level.OFF);
    }

    @BeforeEach
    @AfterEach
    public void cleanUp() {
        File file = new File(TEST_CONFIG_PATH);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    @DisplayName("Creates config file if it doesn't exist using default field values")
    public void createsConfigFileIfMissing() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists());


        //Create the file, check values and file existence
        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH,LOGGER);
        TestConfig default_config = configLoader.loadConfig(TestConfig.class);
        assertEquals("defaultName", default_config.name);
        assertEquals(1234, default_config.port);
        assertEquals(Math.PI*-1, default_config.PI);
        assertEquals((float) Math.E, default_config.e);
        assertTrue(default_config.debug);

        assertTrue(new File(TEST_CONFIG_PATH).exists());


        //Read the config from the file and check variables
        ConfigLoader configLoader2 = new ConfigLoader(TEST_CONFIG_PATH,LOGGER);
        TestConfig config_from_file = configLoader2.loadConfig(TestConfig.class);
        assertEquals("defaultName", config_from_file.name,"Config field 'name' loaded from the file should be the default value after read");
        assertEquals(1234, config_from_file.port,"Config field 'name' loaded from the file should be the default value after read");
        assertEquals(Math.PI*-1, default_config.PI);
        assertEquals((float) Math.E, default_config.e);
        assertTrue(default_config.debug);
    }

    @Test
    @DisplayName("Creates config file manually then reads its content to the config class")
    public void loadsConfigFromManualFile() throws Exception {
        // Create config file manually
        String YAML = "name: newName\n#Ignored@#Comment\nport : 9876\ndebug: false\nPI : 4.121121\ne: 3.12";
        Files.writeString(new File(TEST_CONFIG_PATH).toPath(), YAML);

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH,LOGGER);
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
        TestConfig original = new TestConfig();
        original.name = "unchanged";
        original.port = 5555;

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH,LOGGER);
        TestConfig default_config = configLoader.loadConfig(TestConfig.class);
        String original_content = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        assertEquals("defaultName", default_config.name);
        assertEquals(1234, default_config.port);

        TestConfig reloaded_config = configLoader.loadConfig(TestConfig.class);
        String after_reload = Files.readString(new File(TEST_CONFIG_PATH).toPath());

        assertEquals(original_content, after_reload);
        assertNotEquals(original.name,reloaded_config.name);
        assertNotEquals(original.port,reloaded_config.port);
        assertEquals(original.PI, default_config.PI);
        assertEquals(original.e, default_config.e);
        assertEquals(original.debug, default_config.debug);

    }

    @Test
    @DisplayName("Checks if comments from the config class are preserved in the YAML config file")
    public void checkCommentAnnotationCorrectnessInYaml() throws Exception {

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH,LOGGER);
        TestConfig config = configLoader.loadConfig(TestConfig.class);

        File file = new File(TEST_CONFIG_PATH);
        assertTrue(file.exists(), "YAML file should be created when it doesn't exist");

        String yamlContent  = Files.readString(new File(TEST_CONFIG_PATH).toPath());

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
        // Create config file manually
        String YAML = "name: newName #Ignored@#Comment\nport : 9877";
        Files.writeString(new File(TEST_CONFIG_PATH).toPath(), YAML);

        File file = new File(TEST_CONFIG_PATH);
        assertTrue(file.exists(), "YAML file should be created when it doesn't exist");

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH,LOGGER);
        TestConfig config = configLoader.loadConfig(TestConfig.class);


        String yamlContent  = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        // Check that a field-level inline comment (after the 'name' field) is preserved
        assertFalse(yamlContent.contains("#Ignored@#Comment"),
                "Inline comment preservation is not supported");

        //The reading should be not affected
        assertEquals("newName", config.name);
        assertEquals(9877,config.port);
        assertEquals(Math.PI*-1, config.PI);
        assertEquals((float) Math.E, config.e);
        assertTrue(config.debug);

    }


    @Test
    @DisplayName("Checks manual comment behaviour in the YAML config file")
    public void checkExternalCommentBehaviourInYaml() throws Exception {
        // Create config file manually
        String YAML = "#Ignored@#Comment\nname: newName\nport : 9877";
        Files.writeString(new File(TEST_CONFIG_PATH).toPath(), YAML);

        File file = new File(TEST_CONFIG_PATH);
        assertTrue(file.exists(), "YAML file should be created when it doesn't exist");

        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH,LOGGER);
        TestConfig config = configLoader.loadConfig(TestConfig.class);


        String yamlContent  = Files.readString(new File(TEST_CONFIG_PATH).toPath());
        // Check that a field-level inline comment (after the 'name' field) is preserved
        assertFalse(yamlContent.contains("#Ignored@#Comment"),
                "Manual comment preservation is not supported");

        //The reading should be not affected
        assertEquals("newName", config.name);
        assertEquals(9877,config.port);
        assertEquals(Math.PI*-1, config.PI);
        assertEquals((float) Math.E, config.e);
        assertTrue(config.debug);

    }
}
