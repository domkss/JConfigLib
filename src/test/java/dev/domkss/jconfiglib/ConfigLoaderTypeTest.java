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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;


public class ConfigLoaderTypeTest {
    private static final String TEST_CONFIG_PATH = "build/type-test-config.yaml";
    private static final Logger LOGGER = Logger.getLogger(ConfigLoaderTypeTest.class.getName());
    private static final Random rand = new Random();

    public static class TestConfig {

        @ConfigField
        public int intPrimitive = 1;
        @ConfigField(comment = "The !$%^^Comment")
        public Integer intBoxed = 2;

        @ConfigField
        public float floatPrimitive = 1.1f;
        @ConfigField
        public Float floatBoxed = 2.2f;

        @ConfigField
        public double doublePrimitive = 3.3;
        @ConfigField
        public Double doubleBoxed = 4.4;

        @ConfigField
        public long longPrimitive = 100L;
        @ConfigField
        public Long longBoxed = 200L;

        @ConfigField
        public boolean boolPrimitive = true;
        @ConfigField
        public Boolean boolBoxed = false;

        @ConfigField
        public String stringValue = "default";

        @ConfigField
        public List<Integer> intList = List.of(1, 2, 3);
        @ConfigField
        public List<Float> floatList = List.of(1.1f, 2.2f);
        @ConfigField
        public List<Double> doubleList = List.of(3.3, 4.4);
        @ConfigField
        public List<Boolean> boolList = List.of(true, false, true);
        @ConfigField
        public List<String> stringList = List.of("a", "b");

        @ConfigField
        public Map<String, Integer> intMap = new HashMap<>(Map.of("one", 1, "two", 2));
        @ConfigField
        public Map<String, Float> floatMap = Map.of("fl1", -1.12f);
        @ConfigField
        public Map<String, Double> doubleMap = Map.of("e", 2.71);
        @ConfigField
        public Map<String, Boolean> boolMap = Map.of("yes", true, "no", false);

        @ConfigField
        public Date utilDate = new Date();

        @ConfigField
        public BigInteger bigInteger = new BigInteger("123456789012345678901234567890");
        @ConfigField
        public BigDecimal bigDecimal = new BigDecimal("1234567890.123456789");
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
    void testAllFieldsSavedAndLoadedCorrectly() throws Exception {
        assertFalse(new File(TEST_CONFIG_PATH).exists());

        //Create the file, check values and file existence
        ConfigLoader configLoader = new ConfigLoader(TEST_CONFIG_PATH, LOGGER);
        TestConfig defaultConfig = configLoader.loadConfig(TestConfig.class);
        assertTrue(new File(TEST_CONFIG_PATH).exists());

        //Gather default values to a Map
        Map<String, Object> defaultValues = new HashMap<>();
        for (Field field : TestConfig.class.getDeclaredFields()) {
            if (!field.isAnnotationPresent(ConfigField.class)) continue;
            field.setAccessible(true);

            String name = field.getName();
            Object defaultValue = field.get(defaultConfig);
            defaultValues.put(name, defaultValue);
        }

        //Check fields after reload
        TestConfig unmodifiedReloadedConfig = configLoader.loadConfig(TestConfig.class);
        assertFieldValues(defaultValues, unmodifiedReloadedConfig);

        //Randomize the file content
        Map<String, Object> newValues = randomizeExpectedValuesInTheConfigFile();

        //Reload the config from the file and check field values
        TestConfig modifiedReloadedConfig = configLoader.loadConfig(TestConfig.class);
        assertFieldValues(newValues, modifiedReloadedConfig);


    }


    private void assertFieldValues(Map<String, Object> expectedValues, TestConfig conf) throws IllegalAccessException {
        for (Field field : TestConfig.class.getDeclaredFields()) {
            if (!field.isAnnotationPresent(ConfigField.class)) continue;

            field.setAccessible(true);
            Object expected = expectedValues.get(field.getName());
            Object actual = field.get(conf);

            // Special handling for lists
            if (actual instanceof List<?>) {
                List<?> expectedList = (List<?>) expected;
                List<?> actualList = (List<?>) actual;

                assertEquals(expectedList.size(), actualList.size(), "Size mismatch: " + field.getName());

                for (int i = 0; i < actualList.size(); i++) {
                    assertEquals(expectedList.get(i), actualList.get(i), "Mismatch at index " + i + " on field: " + field.getName());
                }
            }
            // Special handling for maps
            else if (actual instanceof Map<?, ?>) {
                Map<?, ?> expectedMap = (Map<?, ?>) expected;
                Map<?, ?> actualMap = (Map<?, ?>) actual;

                assertEquals(expectedMap.size(), actualMap.size(), "Size mismatch: " + field.getName());

                for (int i = 0; i < actualMap.size(); i++) {
                    assertEquals(expectedMap.get(i), actualMap.get(i), "Mismatch at index " + i + " on field: " + field.getName());
                }
            }
            // Special handling for floating point numbers
            else if (expected instanceof Float) {
                assertEquals((Float) expected, (Float) actual, 0.0001, "Field mismatch: " + field.getName());
            } else if (expected instanceof Double) {
                assertEquals((Double) expected, (Double) actual, 0.0001, "Field mismatch: " + field.getName());
            } else if (expected instanceof BigDecimal expectedBigDecimal) {
                BigDecimal actualBigDecimal = (BigDecimal) actual;
                BigDecimal difference = expectedBigDecimal.subtract(actualBigDecimal).abs();
                assertTrue(difference.compareTo(BigDecimal.valueOf(0.0001)) <= 0,
                        "Field mismatch: " + field.getName() + " Expected: " + expectedBigDecimal + " but was: " + actualBigDecimal);
            } else {
                assertEquals(expected, actual, "Field mismatch: " + field.getName());
            }
        }
    }


    private Map<String, Object> randomizeExpectedValuesInTheConfigFile() throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> yamlData;
        //Load config file with sneakYaml
        try (InputStream input = new FileInputStream(TEST_CONFIG_PATH)) {
            yamlData = yaml.load(input);
        }

        Map<String, Object> expectedValues = new HashMap<>();

        //Randomize field values
        for (Field field : TestConfig.class.getDeclaredFields()) {
            if (!field.isAnnotationPresent(ConfigField.class)) continue;
            field.setAccessible(true);

            String name = field.getName();
            Object newValue = generateRandomValue(field);
            yamlData.put(name, newValue);
            expectedValues.put(name, newValue);
        }

        // Write modified YAML back to file
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        try (Writer output = new FileWriter(TEST_CONFIG_PATH)) {
            new Yaml(options).dump(yamlData, output);
        }

        return expectedValues;

    }

    private Object generateRandomValue(Field field) {
        Type generictype = field.getGenericType();

        // Primitive types and wrappers
        if (generictype == int.class || generictype == Integer.class) return rand.nextInt(1000);
        if (generictype == float.class || generictype == Float.class) return rand.nextFloat() * 100;
        if (generictype == double.class || generictype == Double.class) return rand.nextDouble() * 100;
        if (generictype == long.class || generictype == Long.class) return rand.nextLong();
        if (generictype == boolean.class || generictype == Boolean.class) return rand.nextBoolean();
        if (generictype == String.class) return UUID.randomUUID().toString();
        if (generictype == BigInteger.class) return new BigInteger(50, rand);
        if (generictype == BigDecimal.class)
            return BigDecimal.valueOf(rand.nextDouble() * 1000).setScale(5, RoundingMode.HALF_UP);
        if (generictype == Date.class) return new Date();

        if (generictype instanceof ParameterizedType pt) {
            Class<?> rawType = field.getType();
            // Handle Lists: Check the type of the list elements
            if (List.class.isAssignableFrom(rawType)) {
                // Get the type of the elements inside the List (e.g., Integer, String, Boolean, etc.)
                Class<?> elementType = (Class<?>) pt.getActualTypeArguments()[0];
                return switch (elementType.getName()) {
                    case "int", "java.lang.Integer" -> List.of(rand.nextInt(100), rand.nextInt(100), rand.nextInt(100));
                    case "float", "java.lang.Float" ->
                            List.of(rand.nextFloat() * 100, rand.nextFloat() * 100, rand.nextFloat() * 100);
                    case "double", "java.lang.Double" ->
                            List.of(rand.nextDouble() * 100, rand.nextDouble() * 100, rand.nextDouble() * 100);
                    case "long", "java.lang.Long" -> List.of(rand.nextLong(), rand.nextLong(), rand.nextLong());
                    case "short", "java.lang.Short" ->
                            List.of((short) rand.nextInt(Short.MAX_VALUE), (short) rand.nextInt(Short.MAX_VALUE));
                    case "byte", "java.lang.Byte" ->
                            List.of((byte) rand.nextInt(Byte.MAX_VALUE), (byte) rand.nextInt(Byte.MAX_VALUE));
                    case "java.math.BigInteger" -> List.of(
                            new BigInteger(50, rand),
                            new BigInteger(50, rand)
                    );
                    case "java.math.BigDecimal" -> List.of(
                            BigDecimal.valueOf(rand.nextDouble() * 1000).setScale(5, RoundingMode.HALF_UP),
                            BigDecimal.valueOf(rand.nextDouble() * 1000).setScale(5, RoundingMode.HALF_UP)
                    );
                    case "java.lang.String", "String" ->
                            List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                    case "boolean", "java.lang.Boolean" ->
                            List.of(rand.nextBoolean(), rand.nextBoolean(), rand.nextBoolean());
                    default -> List.of();
                };

            }

            // Handle Maps (assumes Map<String, V>)
            if (Map.class.isAssignableFrom(rawType)) {
                Type keyType = pt.getActualTypeArguments()[0];
                Type valueType = pt.getActualTypeArguments()[1];

                if (keyType != String.class && !keyType.getTypeName().equals("java.lang.String")) {
                    throw new UnsupportedOperationException("Only Map<String, ?> supported");
                }

                Class<?> valueClazz = (Class<?>) valueType;

                return switch (valueClazz.getSimpleName()) {
                    case "Integer", "int" -> Map.of("key1", rand.nextInt(100), "key2", rand.nextInt(100));
                    case "Boolean", "boolean" -> Map.of("key1", rand.nextBoolean(), "key2", rand.nextBoolean());
                    case "Float", "float" -> Map.of("key1", rand.nextFloat() * 100, "key2", rand.nextFloat() * 100);
                    case "Double", "double" -> Map.of("key1", rand.nextDouble() * 100, "key2", rand.nextDouble() * 100);
                    case "String" -> Map.of("key1", UUID.randomUUID().toString(), "key2", UUID.randomUUID().toString());
                    case "BigInteger" -> Map.of("key1", new BigInteger(50, rand), "key2", new BigInteger(50, rand));
                    case "BigDecimal" ->
                            Map.of("key1", BigDecimal.valueOf(rand.nextDouble() * 1000).setScale(5, RoundingMode.HALF_UP),
                                    "key2", BigDecimal.valueOf(rand.nextDouble() * 1000).setScale(5, RoundingMode.HALF_UP));
                    default -> Map.of();  // Fallback for unsupported types
                };
            }
        }

        // Return null for unsupported types
        return null;
    }


}
