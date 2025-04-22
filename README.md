# JConfigLib

**JConfigLib** is a lightweight Java library designed to simplify configuration management for Java applications. It allows you to:

- Populate Java class fields from a YAML configuration file.
- Automatically generate a default config file with comments if it doesn't already exist.

>  **Only YAML configuration files are supported.**
>
> **Minimum Supported Java Version:** Java 17

---

## 🔧 Features

- Easy configuration class mapping using annotations.
- Auto-generates a readable YAML configuration file if missing.
- Supports comments for better documentation.
- Ensures type-safe configuration loading.

---

## 📦 Add JConfigLib to your project

Add JConfigLib to your project using your build tool.

For Gradle:
```groovy
dependencies {
    // Other Dependencies
    implementation "dev.domkss:jconfiglib:1.1.3"
}
```
For Maven:
```xml
    <dependencies>
        <!--Other Dependencies-->
        <dependency>
            <groupId>dev.domkss</groupId>
            <artifactId>jconfiglib</artifactId>
            <version>1.1.3</version>
        </dependency>
    </dependencies>
```


---

## 🧪 Example

### 1. Define your configuration class:

```java
public class ServerConfig {

    // Lists,Maps,Primitive types are supported
    
    @ConfigField(comment = "The domain name of the host")
    private String hostName = "domkss.dev";

    @ConfigField(comment = "Used port")
    private int port = 80;

    @ConfigField
    private Integer maxNumberOfSessions = 3;

    @ConfigField(comment = "Comment explaining what this is used for")
    private List<Integer> usedWeights = List.of(1,24,12);
    
    // Getter definitions to access the fields from your code...

}
```
### 2. Generate the config YAML with default values or load its content if it already exists:
```java
String CONFIG_FILE_PATH = "config/ConfigFileName.yaml"; // Path to your config file location
Logger LOGGER = Logger.getLogger("LoggerName");
ConfigLoader configLoader = new ConfigLoader(CONFIG_FILE_PATH, LOGGER);
try {
    ServerConfig serverConfig = configLoader.loadConfig(ServerConfig.class);
    // If the file already exists serverConfig fields are now populated from the YAML file
    // If the file on CONFIG_FILE_PATH doesn't exist this function call 
    // will try to create it with default values defined in the ServerConfig.class
} catch (Exception e) {
    LOGGER.info("Failed to load config: " + e.toString());
}
```