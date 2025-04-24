JCC = javac
JAVA = java
JFLAGS = -g
JAR = lib/jsch-0.1.55.jar

JFLAGS = --release 11 -cp lib/jsch-0.1.55.jar
SOURCES := $(shell find . -name '*.java')
CLASSES := $(patsubst %,bin/%,$(SOURCES:.java=.class))

bin: $(CLASSES)

bin/%.class: %.java
	@mkdir -p $(dir $@)
	javac $(JFLAGS) -d bin $<


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
	$(RM) *.class Configs/*.class Msgs/*.class Logging/*.class Metadata/*.class Queue/*.class Handler/*.class Process/*.class Tasks/*.class log_*
	$(RM) -r peer_100[2-9]*