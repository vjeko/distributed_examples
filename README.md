export REMOTE=git@github.com:NetSys/sts2-interposition.git
git subtree add --prefix=interposition $REMOTE vjeko
git subtree pull --prefix=interposition $REMOTE vjeko
git subtree push --prefix=interposition $REMOTE vjeko

Run: sbt run
