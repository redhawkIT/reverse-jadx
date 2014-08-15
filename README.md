## JADX

[![Build Status](https://travis-ci.org/skylot/jadx.png?branch=master)](https://travis-ci.org/skylot/jadx)
[![Build Status](https://drone.io/github.com/skylot/jadx/status.png)](https://drone.io/github.com/skylot/jadx/latest)
[![Coverage Status](https://coveralls.io/repos/skylot/jadx/badge.png)](https://coveralls.io/r/skylot/jadx)
[![Coverity Scan Build Status](https://scan.coverity.com/projects/2166/badge.svg)](https://scan.coverity.com/projects/2166)

**jadx** - Dex to Java decompiler

Command line and GUI tools for produce Java source code from Android Dex and Apk files


### Downloads
- [unstable](https://drone.io/github.com/skylot/jadx/files)
- from [github](https://github.com/skylot/jadx/releases)
- from [sourceforge](http://sourceforge.net/projects/jadx/files/)


### Building from source
    git clone https://github.com/skylot/jadx.git
    cd jadx
    ./gradlew dist

(on Windows, use `gradlew.bat` instead of `./gradlew`)

Scripts for run jadx will be placed in `build/jadx/bin`
and also packed to `build/jadx-<version>.zip`


### Run
Run **jadx** on itself:

    cd build/jadx/
    bin/jadx -d out lib/jadx-core-*.jar
    #or
    bin/jadx-gui lib/jadx-core-*.jar


### Usage
```
jadx[-gui] [options] <input file> (.dex, .apk, .jar or .class)
options:
 -d, --output-dir    - output directory
 -j, --threads-count - processing threads count
 -f, --fallback      - make simple dump (using goto instead of 'if', 'for', etc)
     --cfg           - save methods control flow graph to dot file
     --raw-cfg       - save methods control flow graph (use raw instructions)
 -v, --verbose       - verbose output
 -h, --help          - print this help
Example:
 jadx -d out classes.dex
```

### Troubleshooting
##### Out of memory error:
  - Reduce processing threads count (`-j` option) 
  - Increase maximum java heap size:
    * command line (example for linux): 
      `JAVA_OPTS="-Xmx4G" jadx -j 1 some.apk`
    * edit 'jadx' script (jadx.bat on Windows) and setup bigger heap size:
      `DEFAULT_JVM_OPTS="-Xmx2500M"`

---------------------------------------
*Licensed under the Apache 2.0 License*

*Copyright 2014 by Skylot*
