#!/bin/sh

# <copyright>
#  
#  Copyright 2004 BBNT Solutions, LLC
#  under sponsorship of the Defense Advanced Research Projects
#  Agency (DARPA).
# 
#  You can redistribute this software and/or modify it under the
#  terms of the Cougaar Open Source License as published on the
#  Cougaar Open Source Website (www.cougaar.org).
# 
#  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
#  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
#  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
#  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
#  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
#  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
#  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
#  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
#  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
#  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
#  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#  
# </copyright>


export SOCIETY_FILE=society.xml
export NODE=NODE

#rm $NODE.log

java -Dorg.cougaar.node.name=$NODE -Dorg.cougaar.core.agent.startTime=07/11/2005 -Duser.timezone=GMT -Dorg.cougaar.nameserver.auto=true -Dorg.cougaar.core.node.InitializationComponent=XML -Dorg.cougaar.thread.running.max=300 -Dorg.cougaar.core.persistence.enable=false -Dorg.cougaar.core.persistence.clear=true -Dorg.cougaar.core.logging.config.filename=loggingConfig.conf -Dorg.cougaar.install.path=$CIP -Dorg.cougaar.workspace=$CIP/workspace -Dorg.cougaar.system.path=$CIP/sys -Dorg.cougaar.class.path=../classes -Djava.class.path=$CIP/lib/bootstrap.jar -Xbootclasspath/p:$CIP/lib/javaiopatch.jar -Xms128m -Xmx1536m -XX:ThreadStackSize=256 -Dorg.cougaar.core.agent.heartbeat=false -Dorg.cougaar.core.agent.showTraffic=false -Dorg.cougaar.society.file=$SOCIETY_FILE -Dorg.cougaar.config.path=.\; org.cougaar.bootstrap.Bootstrapper org.cougaar.core.node.Node
