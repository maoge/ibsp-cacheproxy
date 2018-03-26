#! /bin/bash

source /etc/profile
source ~/.bash_profile

bin='dirname "$0"'

print_usage()
{
  echo "Usage: COMMAND"
  echo "where COMMAND is one of:"
  echo "  start       run a  access"
  echo "  stop        stop the running access"
}
if [ $# = 0 ]; then
  print_usage
  exit 1
fi

# get arguments
COMMAND=[argument]
ACTION=$1
shift

# some Java parameters
if [ "$JAVA_HOME" != "" ]; then
  #echo "run java in $JAVA_HOME"
  JAVA_HOME=$JAVA_HOME
fi
  
if [ "$JAVA_HOME" = "" ]; then
  echo "Error: JAVA_HOME is not set."
  exit 1
fi

JAVA=$JAVA_HOME/bin/java

# CLASSPATH initially

for f in ../lib/*.jar; do
   CLASSPATH=${CLASSPATH}:$f;
done

for f in ./*.jar; do
   CLASSPATH=${CLASSPATH}:$f;
done
 
CLASSPATH=${CLASSPATH}:../conf;

# get arguments


# configure command parameters
if [ "$COMMAND" = "[argument]" ]; then
  CLASS=com.ctg.itrdc.cache.access.AccessMain
elif [ "$COMMAND" = "version" ]; then
  echo "version 0.1";
  exit 1
else
  print_usage  
  exit 1  
fi

JAVA_OPTS="-server -Xmx5120m -Xms5120m -Xmn800m -XX:PermSize=64m 
    -XX:MaxPermSize=256m -XX:SurvivorRatio=4 
    -verbose:gc -Xloggc:../logs/gc.log
    -Djava.awt.headless=true 
    -XX:+PrintGCTimeStamps -XX:+PrintGCDetails 
    -Dsun.rmi.dgc.server.gcInterval=1800000 -Dsun.rmi.dgc.client.gcInterval=1800000 
    -XX:+UseConcMarkSweepGC -XX:MaxTenuringThreshold=15"

SERVER_NAME="-ServerName $COMMAND"
pid=`ps ax | grep -i $CLASS |grep java | grep $COMMAND | grep -v grep | awk '{print $COMMAND}'`
case $ACTION in
    stop)

    if [ -z "$pid" ] ; then
            echo "No ${COMMAND} is running. please start first !"
            exit -1;
    fi

    kill ${pid}
    echo "The ${COMMAND}(${pid}) is stopped !"
    ;;
    start)

    if [ -n "$pid" ] ; then
            echo "The ${COMMAND}(${pid}) is running, please stop it first!"
            exit -1;
    fi
    
    echo $CLASSPATH
    nohup "$JAVA" $JAVA_OPTS -classpath "$CLASSPATH" $CLASS $SERVER_NAME >/dev/null 2>&1 &
    echo "${COMMAND} started !"
    ;;
	status)

    if [ -z "$pid" ] ; then
            echo "No ${COMMAND} is running now !"
            exit -1;
    fi
	
    echo "The ${COMMAND}(${pid}) is running !"
    ;;
    *)
    echo "Useage: start | stop"
esac