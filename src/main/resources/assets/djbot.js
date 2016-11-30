var urlPrefix = window.location.origin
var playingVideo = false;
var currentlyPlayingRequestId = 0;
var waitingOnNext = false;
var currentVolume = 30;
var usingAuth = false;
var currentUser = null;
var userToken = null;
var player;

//youtube api requires a function with exactly this name. it calls this once after page load
function onYouTubeIframeAPIReady() {
    player = new YT.Player('musicPlayer', {
      width: '600',
      height: '400',
      events: {
          'onError': onYoutubeError
      }
    });
}

function onYoutubeError(event) {
    if(event.data == 100 || event.data == 101 || event.data == 150) {
        //not found, or not allowed to be embedded. hopefully the dj server checked for this, but if not, we skip it now.
        nextSong(true);
    }
}

$.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/authenabled?callback=?',
        success: function(response) {
            if(response) {
                usingAuth = true;
                document.getElementById("userStuff").style.display = "block";
            } else {
                usingAuth = false;
                document.getElementById("nextButton").style.visibility = "visible";
            }
        }
    })

$(document).keydown(function(event){
    if(event.keyCode === 38 && event.altKey && event.ctrlKey) {
        like();
    } else if(event.keyCode === 38 && event.altKey) {
        changevol(10);
    } else if(event.keyCode === 40 && event.altKey) {
        changevol(-10);
    } else if(event.keyCode === 39 && event.altKey) {
        nextSong(true);
    }
});

function login() {
    $.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/login?callback=?',
        success: function(data) {
            if(data.username) {
                currentUser = data.username;
                userToken = data.userToken;
                $("#user").html("user: " + currentUser);
                document.getElementById("nextButton").style.visibility = "visible";
                document.getElementById("volumeCheckbox").style.visibility = "visible";
                document.getElementById("trackVolume").checked = true;
            } else {
                $("#user").html("not logged in");
            }
        }
    })
}

function loadSong(youtubeId, requestId, startTime) {
    player.loadVideoById({
        'videoId': youtubeId,
        'startSeconds': startTime,
        'suggestedQuality': 'large'
    });

    playingVideo = true;
    currentlyPlayingRequestId = requestId;
    $("#likeArea").hide();
}


function playpause() {
    if(player.getPlayerState() !== 1) {
        player.playVideo();
    } else {
        player.pauseVideo();
        playingVideo = false;
    }
}

function applyVolumeChange(vol) {
    currentVolume = vol;
    //youtube volume is linear, switch to a log scale so volume 10->20 is similar to 80->90
    var youtubeVol = Math.pow(Math.E, vol * 0.04605);
    player.setVolume(youtubeVol);
}

function changevol(delta) {
    var newvol = currentVolume + delta;
    if(newvol > 100) newvol = 100;
    if(newvol < 10) newvol = 10;
    applyVolumeChange(newvol);
    if(userToken || !usingAuth) {
        $.ajax({
                dataType: 'json',
                url: urlPrefix + '/djbot/updatevolume?volume=' + newvol + '&userToken=' + userToken + '&callback=?',
                success: function(data) {
                    //nothing to do here now, since we have no error reporting
                }
            })
    }
}

var doingNext = false;
function nextSong(skip) {
    if(doingNext)
        return;

    if(skip) {
        var maybeSkipped = "skip=true&";
    } else {
        var maybeSkipped = "";
    }
    doingNext = true;
    $.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/next?'+maybeSkipped+'currentId=' + currentlyPlayingRequestId + '&userToken=' + userToken + '&callback=?',
        success: function(data) {
            doingNext = false;
            waitingOnNext = false;
            var itWorked = false;
            if( data && data.status === 'success') {
                var newSong = data.song;
                if(newSong && newSong.requestId !== currentlyPlayingRequestId) {
                    loadSong(newSong.videoId, newSong.requestId, data.startSeconds);
                    document.getElementById('title').innerHTML=newSong.title;
                    document.getElementById('requester').innerHTML=newSong.user;
                    itWorked = true;
                }

            }
            if(!itWorked) {
                if(player.getPlayerState() !== 0) {
                    //make the video end even if we got nothing from the server
                    player.seekTo(99999);
                }
            }
        }
    })
}

function loadCurrentSong() {
    $.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/current?callback=?',
        success: function(data) {
            if(data) {
                loadSong(data.videoId, data.requestId, data.startSeconds);
                document.getElementById('title').innerHTML=data.title;
                document.getElementById('requester').innerHTML=data.user;
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

    if(justStarted) {
        loadCurrentSong();
        waitingOnNext = true;
        return;
    }

    try {
        //are we sitting at the end of a video and it's time to load the next?
        if( playingVideo && player && player.getPlayerState() === 0 && !(usingAuth && !userToken)) {
            waitingOnNext = true;
            nextSong(false);
            return;
        }
    } catch (err) {
        console.log(err);
    }

    //see if we need to change the volume or skip the current song
    $.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/check?callback=?',
        success: function(data) {
            if(data) {
                if(!usingAuth || document.getElementById('trackVolume').checked) {
                    applyVolumeChange(data.volume);
                }

                if(data.currentSongId !== 0 && data.currentSongId !== currentlyPlayingRequestId) {
                    player.pauseVideo();
                    waitingOnNext = true;
                    loadCurrentSong();
                }
            }
        }
    })

}

function like() {
    $.ajax({
        dataType: 'json',
        url: urlPrefix + '/djbot/like',
        success: function(data) {
            $("#likeArea").fadeIn(300);
        }
    })

}

justStarted = true;
setInterval(update, 1000);