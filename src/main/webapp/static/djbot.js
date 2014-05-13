urlPrefix = "http://localhost:8069";
playingVideo = true;

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
    //swfobject.embedSWF("https://www.youtube.com/apiplayer?video_id="+youtubeId+"&version=3&feature=player_embedded&autoplay=1&controls=1&enablejsapi=1&modestbranding=0&rel=0&showinfo=1&autohide=0&color=white&playerapiid=musicPlayer&iv_load_policy=3", "musicPlayer", "100%", "100%", "8", null, null, params);
    swfobject.embedSWF("https://www.youtube.com/v/"+youtubeId+"?autoplay=1&controls=1&enablejsapi=0&iv_load_policy=3&playerapiid=musicPlayer", "musicPlayer", "100%", "100%", "8", null, null, params, null);
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
    var maybeSkipped = "skip=true&";
    }

    $.ajax({
        dataType: 'json',
        url: urlPrefix + '/autodj/next?'+maybeSkipped+'&callback=?',
        success: function(data) {
            if(data.status === 'success') {
                if(data.next !== 'none') {
                    var newSong = data.next;
                    if(data.noNewSong !== true) {
                        loadSong(newSong.vid);
                        document.getElementById('title').innerHTML=newSong.title;
                        document.getElementById('requester').innerHTML=newSong.user;
                    }
                }
            }
        }
    })
}

function update() {
    var player = document.getElementById('musicPlayer');
    //if it ended

    if( playingVideo && player && player.getPlayerState() === 0) {
    nextSong(false);
    }

}
setInterval(update, 1000);