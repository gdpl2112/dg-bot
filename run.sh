#!/bin/bash
git pull
java -Dfile.encoding=UTF-8 -classpath "./src/main.jar:./target/dependency/*" io.github.gdpl2112.dg_bot.DgMain