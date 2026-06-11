#!/bin/dash
#


rsync -v --exclude='__pycache__' --exclude=.venv -a --delete tests-android/ pixel9pro-termux:tests-android/ 
