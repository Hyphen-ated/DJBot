#!/bin/bash
echo -ne "\\033]0;djbot server\\007"
java -jar djbot.jar server options.yaml
