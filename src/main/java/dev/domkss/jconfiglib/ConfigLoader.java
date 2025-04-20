package dev.domkss.jconfiglib;


import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ConfigLoader {
    

    private final Logger LOGGER;
    private final String FILE_PATH;



    public ConfigLoader (String filePath,Logger logger){
        this.FILE_PATH=filePath;
        this.LOGGER =logger;
    }


    // Load configuration from the YAML file
    public <T> T loadConfig(Class<T> configClass) {
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
            try (FileInputStream fis = new FileInputStream(configFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(fis);

                if (data == null) {
                    LOGGER.info("Config file is empty or corrupt. Creating with default values.");
                    saveDefaultConfig(configInstance);
                } else {
                    // Process the configuration file
                    updateConfigFromFile(configInstance,data);
                }
            } catch (IOException e) {
                LOGGER.info("Error reading config file, creating a new one with default values.");
                saveDefaultConfig(configInstance);
            }
        }

        return configInstance;
    }

    // Automatically update configuration variables from the YAML file data
    private <T> void updateConfigFromFile(T configInstance,Map<String, Object> fileConfigMap) {
        Map<String, Object> defaultConfigMap = getDefaultValues(configInstance);
        Map<String,Object> updatedConfigMap = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : defaultConfigMap.entrySet()) {
            String key = entry.getKey();
            Object defaultValue = entry.getValue();

            if (fileConfigMap.containsKey(key)) {
                // If the key exists in the YAML file, update the corresponding field
                setFieldValue(configInstance,key, fileConfigMap.get(key));
                updatedConfigMap.put(key,fileConfigMap.get(key)); //Keep the original file value
            } else{
                updatedConfigMap.put(key,defaultValue);

                if(defaultValue!=null && !key.startsWith("#")){
                // If the key does not exist, add the default value to the file and update the field
                LOGGER.log(Level.SEVERE,"Missing '" + key + "' in config, adding default value.");
                setFieldValue(configInstance,key, defaultValue);
                }
            }

        }

        // Save the updated configuration back to the file
        saveConfig(updatedConfigMap);
    }

    // Set the field value based on the field name and value
    private <T>void setFieldValue(T configInstance,String fieldName, Object value) {
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
                    default -> field.set(configInstance, number); // fallback
                }
            } else {
                field.set(configInstance, value); // Strings, booleans, etc.
            }

        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE,"Error setting field value for " + fieldName);
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

                    if(!configAnnotation.comment().isEmpty())
                        defaultValues.put("# "+configAnnotation.comment(), null);

                    defaultValues.put(field.getName(), field.get(configInstance)); // Add the field to map
                }
            }
        } catch (IllegalAccessException e) {
           LOGGER.log(Level.SEVERE,"Error accessing fields while getting default values.");
        }
        return defaultValues;
    }

    // Save the configuration (either updated or default) to the YAML file
    private void saveConfig(Map<String, Object> updatedConfig) {
        File configFile = new File(FILE_PATH);

        try (FileWriter writer = new FileWriter(configFile)) {
            // Write YAML with comments
            for (Map.Entry<String, Object> entry : updatedConfig.entrySet()) {
                if (entry.getKey().startsWith("#")&&entry.getValue()==null) {
                    writer.write(entry.getKey() + "\n");
                }else writer.write(entry.getKey() + ": " + entry.getValue() + "\n\n");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"Error saving config file.");
        }
    }

    // Save the default configuration to the YAML file
    private <T> void saveDefaultConfig(T configInstance) {
        Map<String, Object> defaultConfig = getDefaultValues(configInstance);
        saveConfig(defaultConfig);
    }
}

