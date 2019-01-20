[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Java 9.0+](https://img.shields.io/badge/java-9.0%2b-green.svg)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/com.igormaznitsa/mvn-jlink-wrapper/badge.svg)](http://search.maven.org/#artifactdetails|com.igormaznitsa|mvn-jlink-wrapper|1.0.0|jar)
[![Maven 3.3.9+](https://img.shields.io/badge/maven-3.3.9%2b-green.svg)](https://maven.apache.org/)
[![PayPal donation](https://img.shields.io/badge/donation-PayPal-red.svg)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=AHWJHJFBAWGL2)
[![Yandex.Money donation](https://img.shields.io/badge/donation-Я.деньги-yellow.svg)](http://yasobe.ru/na/iamoss)

![logo](https://raw.githubusercontent.com/raydac/mvn-jlink/master/assets/logo.png)   

# Changelog
__1.0.1 (SNAPSHOT)__
 - improved Liberica file name parser to support chars in JDK version
 - improved bin folder search
 - added existing of jmods folder

__1.0.0 (12-jan-2019)__
 - initial version

# Introduction
Since Java 9, JDK has modules (project Jigsaw) and it provides more or less smoothly way to build JDK versions containing only needed modules. Such formed JDK image can be delivered together with Java application. Because I have several Java based OSS projects (like [SciaReto](http://www.igormaznitsa.com/netbeans-mmd-plugin/) and [ZXPoly emulator](https://github.com/raydac/zxpoly)) which would be nicer with provided pre-built JDK image, I decided to automate processing of JDK image build and have developed the maven plug-in (because Maven is the main tool which I use for OSS projects).

# What does it do?
Functionality of the plugin is very easy, it just provides way to execute tools placed in JDK/bin folder like jdeps and jlink, but sometime it is needed to make image of a specific JDK, for such cases my plugin has internal mechanism which automatically downloads needed variant of OpenJDK from a provider, unpack it and the JDK can be used to build JDK image.   

At present the plug-in supports listed OpenJDK providers:
* LOCAL - locally provided JDK will be used for operations
* [ADOPT](https://adoptopenjdk.net/) - Prebuilt distributives of OpenJDK for many platform, there are `hotspot` and `openj9`.
* [LIBERICA](https://www.bell-sw.com/java.html) - Prebuilt distributives of OpenJDK for many platform including embedded ones, __distributives include JavaFX module__

# Goals and parameters
The plug-in provides four goals:

## Goal `cache-jdk`
The goal just downloads JDK from a provider, unpack it and placing the JDK folder path into Maven custom named project property which can be used by other plug-ins.
### Example
The example of configuration caches OpenJDK from ADOPT provider in project build folder and then save path to its folder into `jlink.jdk.path` project property
```xml
<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>mvn-jlink-wrapper</artifactId>
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
                    <release>jdk8u192-b12</release>
                    <arch>x64</arch>
                    <type>jdk</type>
                    <impl>hotspot</impl>
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
    <executions>
        <execution>
            <id>call-jlink</id>
            <goals>
                <goal>jlink</goal>
            </goals>
            <configuration>
                <jdepsReportPath>${project.build.directory}${file.separator}jdeps.out</jdepsReportPath>
                <output>${project.build.directory}${file.separator}preparedJDK</output>
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
