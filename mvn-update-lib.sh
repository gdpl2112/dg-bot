#!/bin/bash

rm -rf target
mvn -f pom.xml dependency:copy-dependencies -X
