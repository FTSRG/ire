# ire: an Incremental Relational Engine

[![Build Status](https://travis-ci.org/FTSRG/ire.svg)](https://travis-ci.org/FTSRG/ire)

## User's Guide

ire is released on [Bintray](https://bintray.com/ftsrg/maven/ire).

For Maven or Gradle, use the following snippets.

### Maven

```xml
<dependency>
  <groupId>hu.bme.mit</groupId>
  <artifactId>ire</artifactId>
  <version>0.1.0</version>
</dependency>

<repositories>
  <repository>
    <id>ire</id>
    <url>https://ftsrg.bintray.com/maven</url>
  </repository>
</repositories>
```

### Gradle

```groovy
repositories {
	maven { url "http://ftsrg.bintray.com/maven" }
}

dependencies {
	compile 'hu.bme.mit:ire:0.1.0'
}
```

## Contributor's Guide

To build the project, issue the following command:

```bash
gradle clean build
```

The project is implemented in Scala. We recommend the following IDEs:
* IntelliJ IDEA with the [Scala plug-in](https://plugins.jetbrains.com/plugin/?id=1347)
* Eclipse with the [Scala IDE](http://scala-ide.org/). Do not forget to install the **ScalaTest for Scala IDE** plug-in.

## License

The project uses the Apache 2.0 license and is supported by the MTA-BME Lendület Research Group on Cyber-Physical Systems.
