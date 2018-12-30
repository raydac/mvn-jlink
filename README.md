![logo](https://raw.githubusercontent.com/raydac/mvn-jlink/master/assets/logo.png)   
# Introduction
Since Java 9, JDK has modules and some tools which allow to build some image of JDK which contains only needed modules. Because I have several Java based OSS projects (like SciaReto and ZXPoly emulator) which would be nicer with provided prebuilt JDK image, I decided to automate processing of JDK image build and have developed the maven plugin (because maven is the main tool which I use for OSS projects).

# What does it do?
Mainly functionality of the plugin is very easy, it just executes tools placed in JDK bin folder like jdeps and jlink, but sometime it is needed to make image of a specific JDK version or make cross-platform JDK image, for such cases my plugin has internal mechanism which automatically downloads needed variant of OpenJDK from its provider.   
At present listed providers allowed:
* LOCAL - locally provided JDK will be used for operations
* [ADOPT](https://adoptopenjdk.net/) - OpenJDK for needed version, architecture and OS will be downloaded and unpacked for operations
Downloaded JDK is saved on disk in specified cache folder and in future will be used without network operations.
