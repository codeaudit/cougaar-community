#!/bin/sh

export SOCIETY_FILE=society.xml
export NODE=NODE

#rm $NODE.log

java -Dorg.cougaar.node.name=$NODE -Dorg.cougaar.core.agent.startTime=07/11/2005 -Duser.timezone=GMT -Dorg.cougaar.nameserver.auto=true -Dorg.cougaar.core.node.InitializationComponent=XML -Dorg.cougaar.thread.running.max=300 -Dorg.cougaar.core.persistence.enable=false -Dorg.cougaar.core.persistence.clear=true -Dorg.cougaar.core.logging.config.filename=loggingConfig.conf -Dorg.cougaar.install.path=$CIP -Dorg.cougaar.workspace=$CIP/workspace -Dorg.cougaar.system.path=$CIP/sys -Dorg.cougaar.class.path=../classes -Djava.class.path=$CIP/lib/bootstrap.jar -Xbootclasspath/p:$CIP/lib/javaiopatch.jar -Xms128m -Xmx1536m -XX:ThreadStackSize=256 -Dorg.cougaar.core.agent.heartbeat=false -Dorg.cougaar.core.agent.showTraffic=false -Dorg.cougaar.society.file=$SOCIETY_FILE -Dorg.cougaar.config.path=.\; org.cougaar.bootstrap.Bootstrapper org.cougaar.core.node.Node
