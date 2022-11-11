ANTLR_JAR="`pwd`/lib/antlr-4.11.2-SNAPSHOT-complete.jar"
HEAP_JAR="`pwd`/lib/hprof-heap-0.16.jar"

BATCHSIZE="5"
SKIP="1"
NBATCHES="20"

#echo "----------- SPARQL ------------"
#java -Xmx10G -cp "out:$ANTLR_JAR:$HEAP_JAR" TestANTLR4 Sparql query -files '.*\.txt' -batchsize $BATCHSIZE -skip $SKIP -nbatches $NBATCHES input/sparql

echo "----------- Java ------------"
java -Xmx10G -cp "out:$ANTLR_JAR:$HEAP_JAR" TestANTLR4 Java compilationUnit -files '.*\.java' -batchsize $BATCHSIZE -skip $SKIP -nbatches $NBATCHES grammars

#echo "----------- postgresql ------------"
#java -Xmx10G -cp "out:$ANTLR_JAR:$HEAP_JAR" TestANTLR4 PostgreSQL root -files '.*\.sql' -batchsize $BATCHSIZE -skip $SKIP -nbatches $NBATCHES input/postgresql
