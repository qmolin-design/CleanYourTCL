#!/bin/sh
cd "$(dirname "$0")" && docker run --rm --platform linux/amd64 -v "$PWD":/w -w /w cleanyourtv-android-build-amd64 sh -ec '
rm -rf testout && mkdir -p testout
trap "rm -rf testout" EXIT
javac -d testout src/com/example/tvagent/AdbClient.java src/com/example/tvagent/Whitelist.java src/com/example/tvagent/Quota.java test/AdbClientTest.java test/QuotaTest.java
java -cp testout com.example.tvagent.AdbClientTest testdata
java -cp testout com.example.tvagent.QuotaTest'
