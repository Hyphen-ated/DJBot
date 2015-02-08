@echo off
if exist djbot.jar (
   start djbot.jar server options.yaml
) else (
   echo Djbot.jar wasn't found. This probably means either 
   echo #1: You are trying to run this without extracting the zip file it came in.
   echo Extract the zip file.
   echo or, #2: You downloaded the source code
   echo instead of downloading a released version. 
   echo Go to https://github.com/Hyphen-ated/DJBot/releases to download the right thing.
   echo or, #3: You're trying to run it as administrator. Don't do that.
   pause
)