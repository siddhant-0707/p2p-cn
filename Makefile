JCC = javac
JAVA = java
JFLAGS = -g
JAR = lib/jsch-0.1.55.jar

CP  = .:$(JAR)

all: 
    javac -d bin -cp $(CP) $(shell find . -name "*.java")

run:
    java  -cp $(CP):bin Process.StartRemote

default: Peer.class

Peer.class: Process/Peer.java
	$(JCC) $(JFLAGS) Process/Peer.java

Peer: Process/Peer.class
	$(JAVA) Process/Peer 1001

clean:
	$(RM) *.class Configurations/*.class Messages/*.class Logging/*.class Metadata/*.class Queue/*.class Handlers/*.class Process/*.class Tasks/*.class log_*
	$(RM) -r peer_100[2-9]*