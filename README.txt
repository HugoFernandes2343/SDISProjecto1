Run commands on PROJ_1/src

Terminal 1:

javac *.java

rmiregistry

Terminal 2:

java Peer 1.0 1 1 224.0.0.0:8000 224.0.0.0:8001 224.0.0.0:8002

Terminal 3:

java Peer 1.0 2 2 224.0.0.0:8000 224.0.0.0:8001 224.0.0.0:8002

terminal 4:

java Peer 1.0 3 3 224.0.0.0:8000 224.0.0.0:8001 224.0.0.0:8002

Terminal 5:

java TestApp 1 BACKUP 5rpc.pdf 3

java TestApp 1 RESTORE 5rpc.pdf

java TestApp 2 DELETE 5rpc.pdf

java TestApp 3 RECLAIM 500

java TestApp 2 STATE

java TestApp 1 SAVE




