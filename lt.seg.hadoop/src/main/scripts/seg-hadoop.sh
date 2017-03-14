#!/bin/bash

# hadoop options must go as first arguments
HADOOP_JOB_OPTS="-Dmapreduce.job.queuename=shortrunning -Dmapreduce.job.reduces=10000"

hadoop jar \
lt.seg.hadoop-0.7.0-SNAPSHOT-jar-with-dependencies.jar \
de.tudarmstadt.lt.seg.app.HadoopSegmenter \
${HADOOP_JOB_OPTS} \
--key-column 0 \
--text-column 2 \
-f ${in} \
-o ${out}