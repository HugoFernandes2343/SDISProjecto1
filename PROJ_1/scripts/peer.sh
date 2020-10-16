#! /usr/bin/bash
argc=$#

if (( argc != 3 ))
then
	echo "Usage: $0 <version> <peer_id> <svc_access_point>"
	exit 1
fi

ver=$1
id=$2
sap=$3

cd src/build
java Peer ${ver} ${id} ${sap} 224.0.0.0:8000 224.0.0.0:8001 224.0.0.0:8002
