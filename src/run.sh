#!/bin/bash

mkdir -p bin

echo "Compiling..."

javac -d bin -cp "lib/*" $(find . -name "*.java" -not -path "./bin/*")

if [ $? -eq 0 ]; then
    echo "Compilation Successful"
    
    java -cp "bin:lib/*" Main
else
    echo "Compilation Failed"
    exit 1
fi
