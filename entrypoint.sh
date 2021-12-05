#!/bin/sh -l

echo "Starting sbt test"
sbt test
echo "::set-output name=result::$?"