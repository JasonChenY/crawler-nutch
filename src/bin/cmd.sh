#!/bin/bash
if [[ $# -ge 1 ]] && [[ $1 = "5.1" ]]; then
    cp /sdk/tmp/ivy/cache/org.apache.solr/solr-solrj/jars/solr-solrj-5.1.0.jar lib/solr-solrj-4.6.0.jar
    cp /sdk/tmp/ivy/cache/org.apache.zookeeper/zookeeper/jars/zookeeper-3.4.6.jar lib/zookeeper-3.4.5.jar
    cp /sdk/tmp/ivy/cache/org.noggit/noggit/jars/noggit-0.6.jar lib/noggit-0.5.jar
    bin/crawl --solr=http://198.11.181.69:8983/solr/gettingstarted --limit=20 --batches=100 --id=8983 --inject --seeddir=/sdk/tmp/seed.txt
else
    cp /sdk/tmp/ivy/cache/org.apache.solr/solr-solrj/jars/solr-solrj-4.6.0.jar lib/solr-solrj-4.6.0.jar
    cp /sdk/tmp/ivy/cache/org.apache.zookeeper/zookeeper/jars/zookeeper-3.4.5.jar lib/zookeeper-3.4.5.jar
    cp /sdk/tmp/ivy/cache/org.noggit/noggit/jars/noggit-0.5.jar lib/noggit-0.5.jar
    bin/crawl --solr=http://127.0.0.1:8080/solr --limit=20 --batches=100 --id=8080 --inject --seeddir=/sdk/tmp/seed.txt
fi


