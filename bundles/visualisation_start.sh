#!/bin/bash

ETCD_HOST=130.89.227.198 ETCD_PORT=4001 java -Dorg.ops4j.pax.logging.DefaultServiceLog.level=OFF -jar visualisation-0.1.jar
#ETCD_HOST=130.89.227.198 ETCD_POST=4001 java -jar visualisation-0.1.jar