#! /usr/bin/bash
cd src
rm -rf build
mkdir -p build
cp -R files build
javac -d build *.java

