#!/bin/bash

$JAVA_HOME/bin/javac -cp target/classes:src/main/resources --release 9 jdk/internal/loader/ClassLoaderDelegateForJDK9.java
$JAVA_HOME/bin/javac -cp target/classes:src/main/resources --release 8 java/lang/reflect/AccessibleSetterInvokerForJDK9.java
$JAVA_HOME/bin/javac -cp target/classes:src/main/resources --release 8 java/lang/ConsulterRetrieverForJDK9.java

mv src/main/resources/jdk/internal/loader/ClassLoaderDelegateForJDK9.class src/main/resources/org/burningwave/core/jvm/ClassLoaderDelegateForJDK9.bwc
mv src/main/resources/java/lang/reflect/AccessibleSetterInvokerForJDK9.class src/main/resources/org/burningwave/core/jvm/AccessibleSetterInvokerForJDK9.bwc
mv src/main/resources/java/lang/ConsulterRetrieverForJDK9.class src/main/resources/org/burningwave/core/jvm/ConsulterRetrieverForJDK9.bwc

