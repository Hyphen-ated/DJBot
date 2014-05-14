DJBot is an irc bot for twitch that lets viewers request songs. It's open-source and licensed under the GPL! Pull requests are accepted!

Unlike many other popular twitch irc bots, you have to run the bot server yourself on your computer. (because I'm not running one for you)
Also unlike many other bots, DJBot is not intended to be a full-featured administration bot. It's for songrequests and that's it.

Before you can run it, you need to edit options.yaml and put in a bunch of things.
First, make a twitch account for your bot and put its name in the "botName" field.
Next, go to http://twitchapps.com/tmi/ to generate an access token for your bot's twitch account, and put that in the twitchAccessToken field.
(Don't use the access token for your streaming account, it needs to be for the bot account!)

Now you have to make a dropbox account for the bot to host the songlist. Once you do that, go to https://www.dropbox.com/developers/apps and create an app for your copy of the djbot.
Go to the page for the app you just created and click "generate access token", then put that in the dropboxAccessToken field in options.yaml

Don't let other people see these access tokens or they will be able to impersonate your bot on twitch or on dropbox!

Now set "channel" to your twitch streaming account where you want the djbot to join the chat

To run DJBot, you need java installed.
Run startServer.bat to run it.
(if you have java installed but it's not on your PATH, you can edit startServer.bat and replace "java" with an absolute path to your java exe)

Once you have it running and there are no errors, go to http://localhost:8080/djbot/ui/ in your web browser to get the music player.
Now you're good to go, people can use !songrequest <youtube url> in your chat to get songs playing
They can also do !songlist for a link to a text file on dropbox with your current songlist in it.