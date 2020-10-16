#! /usr/bin/bash

argc=$#

if (( argc < 2 ))
then
	echo "Usage: $0 <peer_ap> BACKUP|RESTORE|DELETE|RECLAIM|STATE [<opnd_1> [<optnd_2]]"
	exit 1
fi

pap=$1
oper=$2

case $oper in
BACKUP)
	if(( argc != 4 ))
	then
		echo "Usage: $0 <peer_ap> BACKUP <filename> <rep degree>"
		exit 1
	fi
	opernd_1=$3
	rep_deg=$4
	;;
RESTORE)
	if(( argc != 3 ))
	then
		echo "Usage: $0 <peer_app> RESTORE <filename>"
	fi
	opernd_1=$3
	rep_deg=""
	;;
DELETE)
	if(( argc != 3 ))
	then
		echo "Usage: $0 <peer_app> DELETE <filename>"
		exit 1
	fi
	opernd_1=$3
	rep_deg=""
	;;
RECLAIM)
	if(( argc != 3 ))
	then
		echo "Usage: $0 <peer_app> RECLAIM <max space>"
		exit 1
	fi
	opernd_1=$3
	rep_deg=""
	;;
STATE)
	if(( argc != 2 ))
	then
		echo "Usage: $0 <peer_app> STATE"
		exit 1
	fi
	opernd_1=""
	rep_deg=""
	;;
*)
	echo "Usage: $0 <peer_ap> BACKUP|RESTORE|DELETE|RECLAIM|STATE [<opnd_1> [<optnd_2]]"
	exit 1
	;;
esac

cd src/build

java TestApp ${pap} ${oper} ${opernd_1} ${rep_deg}
