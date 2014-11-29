To compile:

```
$ cd src/
$ sbt clean compile
```

To run:

```
$ sbt run
```

Vjeko notes:

export REMOTE=git@github.com:NetSys/sts2-interposition.git
git subtree add --prefix=interposition $REMOTE vjeko
git subtree pull --prefix=interposition $REMOTE vjeko
git subtree push --prefix=interposition $REMOTE vjeko
