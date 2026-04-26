#!/usr/bin/sh
git switch fork
git branch -D actual
cd ../unsup-actual/
git pull origin trunk
git fast-export --all > ../unsup/repo.bundle
cd ../unsup/
git -c core.symlinks=true fast-import < ./repo.bundle
git branch -m trunk actual
