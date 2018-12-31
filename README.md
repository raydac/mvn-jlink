![logo](https://raw.githubusercontent.com/raydac/mvn-jlink/master/assets/logo.png)   
# Introduction
Since Java 9, JDK has modules (project Jigsaw) and it provides more or less smoothly way to build JDK versions containing only needed modules. Such formed JDK image can be delivered together with Java application. Because I have several Java based OSS projects (like SciaReto and ZXPoly emulator) which would be nicer with provided pre-built JDK image, I decided to automate processing of JDK image build and have developed the maven plug-in (because Maven is the main tool which I use for OSS projects).

# What does it do?
Functionality of the plugin is very easy, it just provides way to execute tools placed in JDK/bin folder like jdeps and jlink, but sometime it is needed to make image of a specific JDK, for such cases my plugin has internal mechanism which automatically downloads needed variant of OpenJDK from a provider, unpack it and the JDK can be used to build JDK image.   

At present listed JDK providers allowed:
* LOCAL - locally provided JDK will be used for operations
* [ADOPT](https://adoptopenjdk.net/) - OpenJDK for needed version, architecture and OS will be downloaded and unpacked for operations
Downloaded JDK is saved on disk in specified cache folder and in future will be used without network operations.

# Goals and parameters
The plug-in provides four goals:

## Goal `cache-jdk`
The goal just downloads JDK from a provider, unpack it and placing the JDK folder path into Maven custom named project property which can be used by other plug-ins.

## Goal `jdeps`
The goal automates work with `JDK/bin/jdeps` utility, it allows to get list of modules needed by a JAR and save result into a file.

## Goal `jlink`
The goal automates work with `JDK/bin/jlink` utility, it allows to build JDK image based on `jdeps` output.

## Goal `jdk-tool`
It is a universal goal, it allows to make call to any tool situated in `JDK/bin` and save its output into files.

# Mind Map of the plug-in
![mindmap](https://raw.githubusercontent.com/raydac/mvn-jlink/master/assets/mindmap.png)
