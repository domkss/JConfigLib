/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2025 Dominik Kiss
 * Repository: https://github.com/domkss/JConfigLib
 */

package dev.domkss.jconfiglib;


import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and manages YAML-based configuration files using annotated configuration classes.
 * <p>
 * This class is responsible for reading configuration data from a YAML file and populating the fields
 * of a user-defined configuration class. If the configuration file exists at the specified path,
 * it will be read and its values will be mapped to the corresponding fields. If the file does not exist,
 * a new one will be created using the default values provided in the configuration class.
 * <p>
 * Example usage:
 * <pre>{@code
 * ConfigLoader loader = new ConfigLoader("path/to/config.yaml", logger);
 * MyConfig config = loader.loadConfig(MyConfig.class);
 * }</pre>
 */
public class ConfigLoader {


    private final Logger LOGGER;
    private final String FILE_PATH;


    public ConfigLoader(String filePath, Logger logger) {
        this.FILE_PATH = filePath;
        this.LOGGER = logger;
    }


    // Load configuration from the YAML file
    public <T> T loadConfig(Class<T> configClass) throws InvalidConfigurationException {
        T configInstance;
        try {
            Constructor<T> constructor = configClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            configInstance = constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            LOGGER.info("Error: The class " + configClass.getName() + " does not have a default constructor. Create a default constructor with no parameters to fix this issue.");
            throw new RuntimeException(e);
        }

        File configFile = new File(FILE_PATH);

        if (!configFile.exists()) {
            LOGGER.info("Config file does not exist. Creating with default values.");
            saveDefaultConfig(configInstance);
        } else {
            try {
                String fileContent = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);

                Yaml yaml = new Yaml();
                LinkedHashMap<String, Object> yamlData = yaml.load(new StringReader(fileContent));

                LinkedHashMap<String, String> comments = extractYamlCommentsFromConfigFile(new StringReader(fileContent));

                LinkedHashMap<String, Object> data = new LinkedHashMap<>();

                for (Map.Entry<String, Object> entry : yamlData.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    String commentKey = "#" + key;
                    if (comments.containsKey(commentKey)) {
                        data.put(commentKey, comments.get(commentKey)); // Insert comment
                    }

                    data.put(key, value); // Insert actual key pair
                }

                if (data.isEmpty()) {
                    LOGGER.info("Config file is empty or corrupt. Creating with default values.");
                    saveDefaultConfig(configInstance);
                } else {
                    // Process the configuration file
                    updateConfigFromFile(configInstance, data);
                }
            } catch (IOException e) {
                LOGGER.info("Error reading config file, creating a new one with default values.");
                saveDefaultConfig(configInstance);
            }
        }

        return configInstance;
    }

    // Automatically update configuration variables from the YAML file data
    private <T> void updateConfigFromFile(T configInstance, Map<String, Object> fileConfigMap) throws InvalidConfigurationException {
        Map<String, Object> defaultConfigMap = getDefaultValues(configInstance);
        Map<String, Object> updatedConfigMap = new LinkedHashMap<>();

        for(Map.Entry<String, Object> entry : fileConfigMap.entrySet()){
            String key = entry.getKey();
            Object value = entry.getValue();

            //Field exists in the config class
            if (!key.startsWith("#") && defaultConfigMap.containsKey(key)) {
                setFieldValue(configInstance, key, value);
            }
            //Keep all key pair in the file
            updatedConfigMap.put(key, value);
        }

        for (Map.Entry<String, Object> entry : defaultConfigMap.entrySet()) {
            String key = entry.getKey();
            Object defaultValue = entry.getValue();

            //If the file already contains the key do nothing
            if(updatedConfigMap.containsKey(key)) continue;

            //Add the key pair if it is not already included in the file
            updatedConfigMap.put(key, defaultValue);

            if (!key.startsWith("#")) {
                LOGGER.log(Level.SEVERE, "Missing '" + key + "' in config, adding default value.");
            }

        }

        // Save the updated configuration back to the file
        saveConfig(updatedConfigMap);
    }

    // Set the field value based on the field name and value
    private <T> void setFieldValue(T configInstance, String fieldName, Object value) throws InvalidConfigurationException {
        try {
            Field field = configInstance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);

            //Cast numbers to the proper type
            if (value instanceof Number number) {
                switch (field.getType().getName()) {
                    case "int", "java.lang.Integer" -> field.set(configInstance, number.intValue());
                    case "float", "java.lang.Float" -> field.set(configInstance, number.floatValue());
                    case "double", "java.lang.Double" -> field.set(configInstance, number.doubleValue());
                    case "long", "java.lang.Long" -> field.set(configInstance, number.longValue());
                    case "short", "java.lang.Short" -> field.set(configInstance, number.shortValue());
                    case "byte", "java.lang.Byte" -> field.set(configInstance, number.byteValue());
                    case "java.math.BigInteger" -> {
                        if (number instanceof BigDecimal bigDecimal) {
                            field.set(configInstance, bigDecimal.toBigInteger());
                        } else {
                            field.set(configInstance, new BigInteger(number.toString()));
                        }
                    }
                    case "java.math.BigDecimal" -> {
                        if (number instanceof BigDecimal bd) {
                            field.set(configInstance, bd);
                        } else {
                            field.set(configInstance, new BigDecimal(number.toString()));
                        }
                    }
                    default -> field.set(configInstance, number); // fallback
                }
            } else if (value instanceof List<?>) {
                field.set(configInstance, castListToMatchType((List<?>) value, field));
            }else if (value instanceof Map<?,?>) {
                field.set(configInstance, castMapToMatchType((Map<?,?>) value, field));
            }
            else {
                field.set(configInstance, value); // Strings, booleans, etc.
            }

        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Error setting field value for " + fieldName);
            throw new InvalidConfigurationException("Error setting field value for " + fieldName);
        }
    }


    // Get default values for all fields annotated with @Config
    private <T> Map<String, Object> getDefaultValues(T configInstance) {
        Map<String, Object> defaultValues = new LinkedHashMap<>();
        try {
            // Loop through all fields in the class
            for (Field field : configInstance.getClass().getDeclaredFields()) {
                // Check if the field has the @Config annotation
                if (field.isAnnotationPresent(ConfigField.class)) {
                    field.setAccessible(true);  // Make private fields accessible
                    ConfigField configAnnotation = field.getAnnotation(ConfigField.class);

                    if (!configAnnotation.comment().isEmpty())
                        defaultValues.put("#" + field.getName(), configAnnotation.comment());

                    defaultValues.put(field.getName(), field.get(configInstance)); // Add the field to map
                }
            }
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.SEVERE, "Error accessing fields while getting default values.");
        }
        return defaultValues;
    }

    // Save the configuration (either updated or default) to the YAML file
    private void saveConfig(Map<String, Object> updatedConfig) {
        File configFile = new File(FILE_PATH);

        Map<String, String> comments = new HashMap<>();
        Map<String, Object> cleanedConfig = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : updatedConfig.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key.startsWith("#") && value instanceof String) {
                comments.put(key.substring(1), value.toString()); // You can also store actual comment text if needed
            } else {
                cleanedConfig.put(key, value);
            }
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);


        try{
            //Dump the comment free config to a YAML string
            String rawYaml = yaml.dump(cleanedConfig);
            StringBuilder finalYaml = new StringBuilder();

            //Inject comments into the YAML string
            String[] lines = rawYaml.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains(":")) {
                    String key = trimmed.split(":")[0];
                    if (comments.containsKey(key)) {
                        finalYaml.append("# ").append(comments.get(key)).append("\n");
                    }
                }
                finalYaml.append(line).append("\n");
            }

            Files.writeString(configFile.toPath(), finalYaml.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving config file.");
        }

    }

    // Save the default configuration to the YAML file
    private <T> void saveDefaultConfig(T configInstance) {
        Map<String, Object> defaultConfig = getDefaultValues(configInstance);
        saveConfig(defaultConfig);
    }

    private LinkedHashMap<String, String> extractYamlCommentsFromConfigFile(Reader reader) throws IOException {
        LinkedHashMap<String, String> comments = new LinkedHashMap<>();
        List<String> pendingComments = new ArrayList<>();

        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.isEmpty()) continue;

                if (trimmed.startsWith("#")) {
                    pendingComments.add(trimmed.substring(1).trim());
                } else if (trimmed.matches("^[^\\s#][^:]*:.*")) {
                    // Found a key line — match key
                    String[] parts = trimmed.split(":", 2);
                    String key = parts[0].trim();

                    if (!pendingComments.isEmpty()) {
                        // Store the entire comment block under #key
                        String joined = String.join("\n", pendingComments);
                        comments.put("#" + key, joined);
                        pendingComments.clear();
                    }
                } else {
                    // Not a comment and not a key — ignore or reset pendingComments
                    pendingComments.clear();
                }
            }
        }

        return comments;
    }

    private List<?> castListToMatchType(List<?> actualList, Field field) {
        if (actualList.isEmpty() || actualList.stream().anyMatch(element -> !(element instanceof Number))) {
            return actualList;
        }
        List<Object> resultList = new ArrayList<>();
        Type generictype = field.getGenericType();

        if (generictype instanceof ParameterizedType pt) {
            Class<?> rawType = field.getType();
            if (List.class.isAssignableFrom(rawType)) {


                // Check the type of the elements in the expected list
                Class<?> elementType = (Class<?>) pt.getActualTypeArguments()[0];


                for (Object element : actualList) {
                    if (element instanceof Number) {
                        switch (elementType.getName()) {
                            case "int", "java.lang.Integer" -> resultList.add(((Number) element).intValue());
                            case "float", "java.lang.Float" -> resultList.add(((Number) element).floatValue());
                            case "double", "java.lang.Double" -> resultList.add(((Number) element).doubleValue());
                            case "long", "java.lang.Long" -> resultList.add(((Number) element).longValue());
                            case "short", "java.lang.Short" -> resultList.add(((Number) element).shortValue());
                            case "byte", "java.lang.Byte" -> resultList.add(((Number) element).byteValue());
                            case "java.math.BigInteger" -> {
                                if (element instanceof BigInteger) {
                                    resultList.add(element);
                                } else {
                                    resultList.add(new BigInteger(element.toString()));
                                }
                            }
                            case "java.math.BigDecimal" -> {
                                if (element instanceof BigDecimal) {
                                    resultList.add(element);
                                } else {
                                    resultList.add(new BigDecimal(element.toString()));
                                }
                            }
                            default -> resultList.add(((Number) element).intValue()); // fallback
                        }
                    } else {
                        throw new IllegalArgumentException("The list contains non-number elements: " + element);
                    }

                }

                return resultList;
            }
        }
        throw new IllegalArgumentException("Unsupported List type");

    }

    private Map<?, ?> castMapToMatchType(Map<?, ?> actualMap, Field field) {
        if (actualMap.isEmpty() || actualMap.values().stream().anyMatch(value -> !(value instanceof Number))) {
            return actualMap;
        }

        Type generictype = field.getGenericType();

        if (generictype instanceof ParameterizedType pt) {
            Class<?> rawType = field.getType();
            if (Map.class.isAssignableFrom(rawType)) {

                // Check the type of key elements in the map
                Class<?> keyElementType = (Class<?>) pt.getActualTypeArguments()[0];
                if(keyElementType != String.class){
                    throw new IllegalArgumentException("Unsupported Map type");
                }

                // Check the type of the elements in the expected list
                Class<?> valueElementType = (Class<?>) pt.getActualTypeArguments()[1];
                Map<Object, Object> resultMap = new HashMap<>();

                for (Map.Entry<?, ?> entry : actualMap.entrySet()) {
                    Object actualKey = entry.getKey();
                    Object actualValue = entry.getValue();

                    if (actualValue instanceof Number) {
                        switch (valueElementType.getName()) {
                            case "int", "java.lang.Integer" ->
                                    resultMap.put(actualKey, ((Number) actualValue).intValue());
                            case "float", "java.lang.Float" ->
                                    resultMap.put(actualKey, ((Number) actualValue).floatValue());
                            case "double", "java.lang.Double" ->
                                    resultMap.put(actualKey, ((Number) actualValue).doubleValue());
                            case "long", "java.lang.Long" ->
                                    resultMap.put(actualKey, ((Number) actualValue).longValue());
                            case "short", "java.lang.Short" ->
                                    resultMap.put(actualKey, ((Number) actualValue).shortValue());
                            case "byte", "java.lang.Byte" ->
                                    resultMap.put(actualKey, ((Number) actualValue).byteValue());
                            case "java.math.BigInteger" -> {
                                if (actualValue instanceof BigInteger) {
                                    resultMap.put(actualKey, actualValue);
                                } else {
                                    resultMap.put(actualKey, new BigInteger(actualValue.toString()));
                                }
                            }
                            case "java.math.BigDecimal" -> {
                                if (actualValue instanceof BigDecimal) {
                                    resultMap.put(actualKey, actualValue);
                                } else {
                                    resultMap.put(actualKey, new BigDecimal(actualValue.toString()));
                                }
                            }
                            default -> resultMap.put(actualKey, ((Number) actualValue).intValue()); // fallback
                        }
                    } else {
                        throw new IllegalArgumentException("The list contains non-number elements: " + actualValue);
                    }
                }

                return resultMap;
            }
        }

        throw new IllegalArgumentException("Unsupported Map type");
    }

    public static class InvalidConfigurationException extends Exception {
        public InvalidConfigurationException(String message) {
            super(message);
        }
    }

}

