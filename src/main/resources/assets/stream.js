urlPrefix = window.location.origin
playingVideo = false;
justStarted = true;
waitingOnNext = false;

function loadSong(youtubeId, requestId, startTime) {
    var params = { allowScriptAccess: "always"};
    //switch to this one for no player controls in the embed
    //swfobject.embedSWF("https://www.youtube.com/apiplayer?video_id="+youtubeId+"&version=3&feature=player_embedded&autoplay=1&controls=1&enablejsapi=1&modestbranding=0&rel=0&showinfo=1&autohide=0&color=white&playerapiid=youtubePlayer&iv_load_policy=3", "youtubePlayer", "600", "400", "8", null, null, params);
    swfobject.embedSWF("https://www.youtube.com/v/"+youtubeId+"?autoplay=1&start="+startTime+"&controls=1&enablejsapi=0&iv_load_policy=3&playerapiid=youtubePlayer", "youtubePlayer", "600", "30", "8", null, null, params, null);
    playingVideo = true;
    currentlyPlayingRequestId = requestId;
}

function loadCurrentSong() {
    $.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/current?callback=?',
        success: function(data) {
            if(data) {
                loadSong(data.videoId, data.requestId, data.startSeconds);
//                document.getElementById('title').innerHTML=data.title;
//                document.getElementById('requester').innerHTML=data.user;
                justStarted = false;
                waitingOnNext = false;
            }
        }
    })
}

function update() {
    if(waitingOnNext) {
        return;
    }

    var player = document.getElementById('youtubePlayer');

    if(justStarted) {
        loadCurrentSong();
        waitingOnNext = true;
        return;
    }

    try {
        //are we sitting at the end of a video and it's time to load the next?
        if( playingVideo && player && player.getPlayerState() === 0) {
            waitingOnNext = true;
            return;
        }
    } catch (err) {
        console.log(err);
    }

    //see if we need to skip the current song
    $.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/check?callback=?',
        success: function(data) {
            if(data) {
                var player = document.getElementById('youtubePlayer');

                if(data.currentSongId !== 0 && data.currentSongId !== currentlyPlayingRequestId) {
                    player.pauseVideo();
                    waitingOnNext = true;
                    loadCurrentSong();
                }
            }
        }
    })

}



function format_stream( twitch_name ) {
    return '<object type="application/x-shockwave-flash" class="swf_stream" bgcolor="#000000">'+
    '<param name="allowFullScreen" value="true">'+
    '<param name="allowScriptAccess" value="always">'+
    '<param name="allowNetworking" value="all">'+
    '<param name="movie" value="http://www.twitch.tv/widgets/live_embed_player.swf">'+
    '<param name="flashvars" value="hostname=www.twitch.tv&channel='+twitch_name+'&auto_play=true&eventsCallback=onPlayerEvent">'+
    '</object>';
}

function formatStream(twitch_name) {
    return '<iframe src="http://www.twitch.tv/' + twitch_name + '/embed" frameborder="0" scrolling="no" height="500" width="620"></iframe>';
}

function formatChat(twitch_name) {
    return '<iframe src="http://www.twitch.tv/' + twitch_name + '/chat?popout=" frameborder="0" scrolling="no" height="500" width="350"></iframe>';
}


$.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/streamname?callback=?',
        success: function(response) {
            document.getElementById('streamDiv').innerHTML=formatStream(response);
            document.getElementById('chatDiv').innerHTML=formatChat(response);

        }
    })

justStarted = true;
setInterval(update, 1000);

