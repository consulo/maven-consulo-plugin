# Project example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0"
		 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<!--Consulo parent project, which provide Consulo dependencies in provided scope (not included in artifact) -->
	<parent>
		<groupId>consulo</groupId>
		<artifactId>arch.ide-provided</artifactId>
		<version>2-SNAPSHOT</version>
		<relativePath/>
	</parent>

	<groupId>consulo.plugin</groupId>
	<artifactId>com.intellij.spellchecker</artifactId>
	<version>2-SNAPSHOT</version>
	<!--consulo plugin packaging type-->
	<packaging>consulo-plugin</packaging>

	<!--Consulo maven repository for dependencies & parent project-->
	<repositories>
		<repository>
			<id>consulo</id>
			<url>https://maven.consulo.io/repository/snapshots/</url>
			<snapshots>
				<enabled>true</enabled>
				<updatePolicy>always</updatePolicy>
			</snapshots>
		</repository>
	</repositories>

	<build>
		<plugins>
			<plugin>
				<groupId>consulo.maven</groupId>
				<artifactId>consulo-maven-plugin</artifactId>
				<version>2-SNAPSHOT</version>
				<extensions>true</extensions>
				<configuration>
					<!--by default plugin id is artifactId from project-->
					<id>${project.artifactId}</id>
					<packaging>
						<!--do not build plugin artifact in dev mode-->
						<!--'dev.mode' is parent property, it's true when no BUILD_NUMBER env var set-->
						<skip>${dev.mode}</skip>
						<!--change version in plugin.xml on building-->
						<version>${build.number}</version>
					</packaging>
					<execution>
						<!--set nightly channel for executing-->
						<repositoryChannel>nightly</repositoryChannel>
					</execution>
				</configuration>
				<executions>
					<execution>
						<phase>package</phase>
						<goals>
							<!--build plugin workspace for sandbox-->
							<goal>workspace</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>

	<dependencies>
		<!-- Add platform test dependency -->
		<dependency>
			<groupId>${project.parent.groupId}</groupId>
			<artifactId>consulo-lang-impl-testing</artifactId>
			<version>${project.parent.version}</version>
			<scope>test</scope>
		</dependency>
	</dependencies>
</project>
```