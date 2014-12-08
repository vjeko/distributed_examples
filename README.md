export REMOTE=git@github.com:NetSys/sts2-interposition.git

git subtree add --prefix=interposition $REMOTE dpor
git subtree pull --prefix=interposition $REMOTE dpor
git subtree push --prefix=interposition $REMOTE dpor

Run: sbt run
