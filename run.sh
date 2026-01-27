#!/bin/bash
git pull
java -Dfile.encoding=UTF-8 -XX:+UseG1GC -classpath "./src/main.jar:./target/dependency/*" io.github.gdpl2112.dg_bot.DgMain