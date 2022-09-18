[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Java 9.0+](https://img.shields.io/badge/java-9.0%2b-green.svg)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/com.igormaznitsa/mvn-jlink-wrapper/badge.svg)](http://search.maven.org/#artifactdetails|com.igormaznitsa|mvn-jlink-wrapper|1.1.1|jar)
[![Maven 3.3.9+](https://img.shields.io/badge/maven-3.3.9%2b-green.svg)](https://maven.apache.org/)
[![PayPal donation](https://img.shields.io/badge/donation-PayPal-cyan.svg)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=AHWJHJFBAWGL2)
[![YooMoney donation](https://img.shields.io/badge/donation-Yoo.money-blue.svg)](https://yoomoney.ru/to/41001158080699)

![logo](https://raw.githubusercontent.com/raydac/mvn-jlink/master/assets/logo-git.png)

# Changelog

__1.1.1 (23-nov-2021)__
 - fixed mime type processing for Github authorized requests [#9](https://github.com/raydac/mvn-jlink/issues/9) 
 - updated dependencies

__1.1.0 (27-mar-2020)__
 - improved processing of Gateway Timeout response
 - reworked `ADOPT` provider to work through [AdoptOpenJdk API V3](https://api.adoptopenjdk.net/swagger-ui/)
 - added `authorization` property which will be provided through `Authorization` header
 - added tests
 - improved `ADOPTGIT` provider
 - added logging for detected limit remaining headers
   
[full changelog](CHANGELOG.txt)

# Introduction
Since Java 9, JDK has modules (project Jigsaw) and it provides more or less smoothly way to build JDK versions containing only needed modules. Such formed JDK image can be delivered together with Java application. Because I have several Java based OSS projects (like [SciaReto](http://www.igormaznitsa.com/netbeans-mmd-plugin/) and [ZXPoly emulator](https://github.com/raydac/zxpoly)) which would be nicer with provided pre-built JDK image, I decided to automate processing of JDK image build and have developed the maven plug-in (because Maven is the main tool which I use for OSS projects).

# What does it do?
Functionality of the plugin is very easy, it just provides way to execute tools placed in JDK/bin folder like jdeps and jlink, but sometime it is needed to make image of a specific JDK, for such cases my plugin has internal mechanism which automatically downloads needed variant of OpenJDK from a provider, unpack it and the JDK can be used to build JDK image.   

At present the plug-in supports listed OpenJDK providers:
* __LOCAL__ - locally provided JDK will be used for operations
* __[BELLSOFT](https://www.bell-sw.com/java.html)__ - Prebuilt distributives of OpenJDK 'LIBERICA' for many platforms including embedded ones, __distributives include JavaFX module__
* __[ADOPT](https://adoptopenjdk.net/)__ - Prebuilt distributives of OpenJDK for many platforms. [It uses AdaptOpenJDK API V3](https://api.adoptopenjdk.net/swagger-ui/).
* __[ADOPTGIT](https://github.com/AdoptOpenJDK)__ - Prebuilt AdoptOpenJDK distributives for many platform hosted by GitHub.
* __[SAPMACHINE](https://github.com/SAP/SapMachine)__ - Prebuilt distributives of OpenJDK provided by SAP.
* __[GRAALVMCE](https://github.com/graalvm/graalvm-ce-builds)__ - Prebuilt JDK distributives of GraalVM Community Edition.
Each provider has its own set of properties to find needed JDK version, check documentation. If it is impossible to find needed JDK then list of all found distributives will be printed and plugin execution will be failed.

# Goals and parameters
The plug-in provides four goals:

## Goal `cache-jdk`
The goal just downloads JDK from a provider, unpack it and placing the JDK folder path into Maven custom named project property which can be used by other plug-ins.
### Examples
Code snippet below shows caching of GraalVM CE, the GraalVM distributive will be automatically downloaded and unpacked into plug-in cache and its path will be provided in maven project through `jlink.jdk.path` property     
```xml
<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>mvn-jlink-wrapper</artifactId>
    <version>1.1.0</version>
    <executions>
        <execution>
            <id>cache-jdk11-graalvmce</id>
            <goals>
                <goal>cache-jdk</goal>
            </goals>
            <configuration>
                <jdkPathProperty>jlink.jdk.path</jdkPathProperty>
                <provider>GRAALVMCE</provider>
                <providerConfig>
                   <type>java11</type>
                   <version>19.3.1</version>
                   <arch>amd64</arch>
                </providerConfig>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Code snippet below shows configuration to cache OpenJDK from ADOPT provider in project build folder and then save path to its folder into `jlink.jdk.path` project property
```xml
<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>mvn-jlink-wrapper</artifactId>
    <version>1.1.0</version>
    <executions>
        <execution>
            <id>cache-jdk-8</id>
            <goals>
                <goal>cache-jdk</goal>
            </goals>
            <configuration>
                <jdkPathProperty>jlink.jdk.path</jdkPathProperty>
                <jdkCachePath>${project.build.directory}${file.separator}jdkCache</jdkCachePath>

                <provider>ADOPT</provider>
                <providerConfig>
                    <release>jdk-14+36</release>
                    <arch>x64</arch>
                    <impl>hotspot</impl>
                    <project>jdk</project>
                </providerConfig>

            </configuration>
        </execution>
    </executions>
</plugin>
```

## Goal `jdeps`
The goal automates work with `JDK/bin/jdeps` utility, it allows to get list of modules needed by a JAR and save result into a file.
### Example
The example calls jdeps tool from provided JDK over project jar file and saves output into `jdeps.out` situated in project build folder.
```xml
<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>mvn-jlink-wrapper</artifactId>
    <version>1.1.0</version>
    <executions>
        <execution>
            <id>call-jdeps</id>
            <goals>
                <goal>jdeps</goal>
            </goals>
            <configuration>
                <output>${project.build.directory}${file.separator}jdeps.out</output>
                <options>
                    <option>${project.build.directory}${file.separator}${project.build.finalName}.jar</option>
                </options>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Goal `jlink`
The goal automates work with `JDK/bin/jlink` utility, it allows to build JDK image based on `jdeps` output.
### Example
The example calls `jlink` from provided JDK and build JDK version based on report provided by `jdeps` tool in `jdeps.out` file, also `java.compiler` module will be added. The prepared JDK version will be presented in project build folder, subfolder `preparedJDK`
```xml
<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>mvn-jlink-wrapper</artifactId>
    <version>1.1.0</version>
    <executions>
        <execution>
            <id>call-jlink</id>
            <goals>
                <goal>jlink</goal>
            </goals>
            <configuration>
                <jdepsReportPath>${project.build.directory}${file.separator}jdeps.out</jdepsReportPath>
                <output>${project.build.directory}${file.separator}preparedJDK</output>
                <modulePaths>
                    <path>${java.home}${file.separator}jmods</path>
                </modulePaths>
                <addModules>
                    <module>java.compiler</module>
                </addModules>
                <options>
                    <option>--compress=2</option>
                    <option>--no-header-files</option>
                    <option>--no-man-pages</option>
                </options>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Goal `jdk-tool`
It is a universal goal, it allows to make call to any tool situated in `JDK/bin` and save its output into files.
### Example
The example calls jps tool from provided tool JDK with 5 seconds timeout and its output will be written into `jps.out` file.
```xml
<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>mvn-jlink-wrapper</artifactId>
    <version>1.1.0</version>
    <executions>
        <execution>
            <id>call-tool</id>
            <phase>package</phase>
            <goals>
                <goal>jdk-tool</goal>
            </goals>
            <configuration>
                <output>${project.build.directory}${file.separator}jps.out</output>
                <tool>jps</tool>
                <timeout>5</timeout>
                <options>
                    <option>-m</option>
                </options>
            </configuration>
        </execution>
    </executions>
</plugin>
```

# Mind Map of the plug-in
Created with [SciaReto](http://sciareto.org)   
![mindmap](https://raw.githubusercontent.com/raydac/mvn-jlink/master/assets/mindmap.png)
