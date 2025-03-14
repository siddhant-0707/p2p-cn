JCC = javac
JAVA = java
JFLAGS = -g

default: Peer.class

Peer.class: Process/Peer.java
	$(JCC) $(JFLAGS) Process/Peer.java

Peer: Process/Peer.class
	$(JAVA) Process/Peer 1001

clean:
	$(RM) *.class Configs/*.class Msgs/*.class Logging/*.class Metadata/*.class Queue/*.class Handler/*.class Process/*.class