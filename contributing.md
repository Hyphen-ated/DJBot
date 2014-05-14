How to build:
"mvn clean package assembly:single" will produce an end-user-facing zipfile in target/

If you are going to be developing you will want to be able to run the bot without needing to do that.
The main class is in hyphenated.djbot.DjApplication, set up your IDE to run it with the arguments "server testingOptions.yaml"
And make a file called testingOptions.yaml; put credentials for your bot in it. Make sure not to commit this file to git!

If you want to fix bugs or write features that's awesome and I will happily work with you! Of particular need of attention is the crappy
html+js I wrote :)

