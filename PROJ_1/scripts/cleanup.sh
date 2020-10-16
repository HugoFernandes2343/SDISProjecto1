#! /usr/bin/bash

argc=$#

if ((argc == 1 ))
then
	peer_id=$1
	rm -Rf src/build/fileSystem/$peer_id/backup/*
	rm -Rf src/build/fileSystem/$peer_id/restore/*
else
	echo "Usage: $0 [<peer_id>]]"
	exit 1
fi

