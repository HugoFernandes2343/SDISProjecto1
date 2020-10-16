#! /usr/bin/bash

argc=$#
port=""

if (( argc != 1 ))
then
	echo "Usage: $0 [<port_no>]]"
	exit 1
else
  port=$1
fi
cd src/build
rmiregistry $port
