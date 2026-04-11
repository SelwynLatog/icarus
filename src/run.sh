# Bash run script
#!/bin/bash

# Create bin directory if it doesn't exist
mkdir -p bin

# Compile all Java files into bin directory
echo "Compiling..."

# Find all .java files recursively and compile them
javac -d bin -cp "lib/*" $(find . -name "*.java" -not -path "./bin/*")

# Check if compilation was successful
if [ $? -eq 0 ]; then
    echo "Nays wan"
    
    # Run the application
    java -cp "bin:lib/*" Main
else
    echo "Ayay ka!"
    exit 1
fi
