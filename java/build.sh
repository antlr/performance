ANTLR_JAR="`pwd`/lib/antlr4.10-SNAPSHOT-complete.jar"
BASE=`pwd`

if [ ! -f /tmp/grammars-v4.zip ]
then
echo "Download sample grammars"
curl -o /tmp/grammars-v4.zip -L https://github.com/antlr/grammars-v4/archive/refs/heads/master.zip
fi
echo "Unzip sample grammars"
cd /tmp
rm -rf /tmp/grammars-v4-master
unzip -q grammars-v4.zip
cd $BASE
echo "Copy sample grammars and input"
mkdir grammars
cp /tmp/grammars-v4-master/java/java/*.g4 \
   /tmp/grammars-v4-master/sql/postgresql/*.g4 \
   /tmp/grammars-v4-master/sparql/*.g4 \
   grammars
cp /tmp/grammars-v4-master/sql/postgresql/Java/*.java grammars # support code
mkdir -p input/mysql
mkdir -p input/sparql
mkdir -p input/postgresql
cp /tmp/grammars-v4-master/sql/postgresql/examples/join.sql input/postgresql
cp /tmp/grammars-v4-master/sql/postgresql/examples/foreign_key.sql input/postgresql
cp /tmp/grammars-v4-master/sql/postgresql/examples/numeric.sql input/postgresql
cp /tmp/grammars-v4-master/sparql/examples/* input/sparql

echo "Building parsers"
cd grammars
java -jar $ANTLR_JAR *.g4
cd $BASE/src

echo
echo "Compiling parsers and test rig"
javac -cp $ANTLR_JAR:$CLASSPATH -d ../out *.java ../grammars/*.java
cd $BASE
