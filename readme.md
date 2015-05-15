DJBot is an irc bot for twitch that lets viewers request songs. It's open-source and licensed under the GPL! Pull requests are accepted!

Download the latest version here: https://github.com/Hyphen-ated/DJBot/releases

Unlike many other popular twitch irc bots, you have to run the bot server yourself on your computer. (because I'm not running one for you)
Also unlike many other bots, DJBot is not intended to be a full-featured administration bot. It's for songrequests and that's it.

Before you can run it, you need to edit options.yaml and put in a bunch of things.
First, make a twitch account for your bot and put its name in the "botName" field.
Next, go to http://twitchapps.com/tmi/ to generate an access token for your bot's twitch account, and put that in the twitchAccessToken field.
(Don't use the access token for your streaming account, it needs to be for the bot account!)

Now you have to make a dropbox account for the bot to host the songlist. Once you do that, go to https://www.dropbox.com/developers/apps and create an app for your copy of the djbot.
Dropbox will ask you a few questions: It should be of type "dropbox api app", it should be able to edit files and datastores, and it only needs to edit files that it creates itself.
Go to the page for the app you just created and click "generate access token", then put that in the dropboxAccessToken field in options.yaml

Now you need a youtube developer key. Go to https://console.developers.google.com and create a project. Name it whatever you want.
Then click "APIs and Auth" on the left, then click "APIs" under that. Click "Youtube Data API". Click "Enable API".
Then click "Credentials" on the left. Click "Create New Key" under "Public API Access". Click "Server Key". Click "Create".
Copy the API key it shows you and put that in the youtubeAccessToken field in options.yaml.

Don't let other people see these access tokens or they will be able to impersonate your bot on twitch, google, or dropbox!
This includes the logs shown in the djbot window.

Now set "channel" to your twitch streaming account where you want the djbot to join the chat

To run DJBot, you need java installed. If you're not sure whether you have java, you probably don't; go install it.
Run startServer.bat to run the DJBot server.

If it launches an archiving program instead of the djbot, it means your .jar extension needs to be associated with javaw.exe. Try using this utility to restore it: http://johann.loefflmann.net/en/software/jarfix/index.html (or if you don't trust this random program, set it yourself in windows)

Once you have it running and there are no errors, go to http://localhost:8080/djbot/ui/ in your web browser to get the music player.
Now you're good to go, people can use !songrequest <youtube url> in your chat to get songs playing
They can also do !songlist for a link to a text file on dropbox with your current songlist in it.
