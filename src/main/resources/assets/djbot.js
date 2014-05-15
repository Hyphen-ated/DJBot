urlPrefix = "http://localhost:8080";
playingVideo = false;
currentlyPlayingRequestId = 0;
waitingOnSoftNext = false;

$(document).keydown(function(event){
    if(event.keyCode === 38 && event.altKey) {
        changevol(10);
    }
    if(event.keyCode === 40 && event.altKey) {
        changevol(-10);
    }
    if(event.keyCode === 39 && event.altKey) {
        nextSong(true);
    }
});

function loadSong(youtubeId) {
    var params = { allowScriptAccess: "always"};
    //switch to this one for no player controls in the embed
    //swfobject.embedSWF("https://www.youtube.com/apiplayer?video_id="+youtubeId+"&version=3&feature=player_embedded&autoplay=1&controls=1&enablejsapi=1&modestbranding=0&rel=0&showinfo=1&autohide=0&color=white&playerapiid=musicPlayer&iv_load_policy=3", "musicPlayer", "600", "400", "8", null, null, params);
    swfobject.embedSWF("https://www.youtube.com/v/"+youtubeId+"?autoplay=1&controls=1&enablejsapi=0&iv_load_policy=3&playerapiid=musicPlayer", "musicPlayer", "600", "400", "8", null, null, params, null);
    playingVideo = true;
}


function playpause() {
    var player = document.getElementById('musicPlayer');
    if(player.getPlayerState() !== 1) {
        player.playVideo();
    } else {
        player.pauseVideo();
        playingVideo = false;
    }
}

function changevol(delta) {
    var player = document.getElementById('musicPlayer');
    var newvol = player.getVolume() + delta;
    if(newvol > 100) newvol = 100;
    if(newvol < 10) newvol = 10;
    player.setVolume(newvol);
}

function nextSong(skip) {
    if(skip) {
        var maybeSkipped = "skip=true&idToSkip=" + currentlyPlayingRequestId;
    } else {
        var maybeSkipped = "";
    }

    $.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/next?'+maybeSkipped+'&callback=?',
        success: function(data) {
            waitingOnSoftNext = false;
            var itWorked = false;
            if( data && data.status === 'success') {
                if(data.next !== 'none') {
                    var newSong = data.next;
                    if(data.noNewSong !== true) {
                        loadSong(newSong.vid);
                        document.getElementById('title').innerHTML=newSong.title;
                        document.getElementById('requester').innerHTML=newSong.user;
                        currentlyPlayingRequestId = newSong.requestId;
                        itWorked = true;
                    }
                }
            }
            if(!itWorked) {
                var player = document.getElementById('musicPlayer');
                if(player.getPlayerState() !== 0) {
                    //make the video end even if we got nothing from the server
                    player.seekTo(99999);
                }
            }
        }
    })
}

function tryFirstSong() {
    $.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/current?callback=?',
        success: function(data) {
            if(data) {
                loadSong(data.videoId);
                document.getElementById('title').innerHTML=data.title;
                document.getElementById('requester').innerHTML=data.user;
                justStarted = false;
            }
        }
    })
}


function update() {
    var player = document.getElementById('musicPlayer');

    if(justStarted) {
        tryFirstSong();
    }

    if( playingVideo && !waitingOnSoftNext && player && player.getPlayerState() === 0) {
        waitingOnSoftNext = true;
        nextSong(false);
    }

}

justStarted = true;
setInterval(update, 1000);