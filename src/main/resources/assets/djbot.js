var urlPrefix = window.location.origin
var paused = true;
var currentlyPlayingRequestId = 0;
var waitingOnNext = false;
var currentVolume = 30;
var usingAuth = false;
var currentUser = null;
var userToken = null;
var player;
//soundcloud variables
var useYoutubePlayer = true;
var soundcloudWidget;
var soundcloudFinishedSong = false;

//youtube api requires a function with exactly this name. it calls this once after page load
function onYouTubeIframeAPIReady() {
    player = new YT.Player('youtubePlayer', {
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

function setupSoundcloudWidget() {
	//associates the iframe sc-widget with the SC Widget API
	soundcloudWidget = SC.Widget('sc-widget');

    soundcloudWidget.bind(SC.Widget.Events.FINISH, function() {
		soundcloudFinishedSong = true;
	});
}

//figure out whether we're using authentication for the djbot user
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

//register shortcut keys for liking, volume, and next
$(document).keydown(function(event){
    if(event.keyCode === 38 && event.altKey && event.ctrlKey) { //crtl-alt-up
        like();
    } else if(event.keyCode === 38 && event.altKey) { //alt-up
        changevol(10);
    } else if(event.keyCode === 40 && event.altKey) { //alt-down
        changevol(-10);
    } else if(event.keyCode === 39 && event.altKey) { //alt-right
        nextSong(true);
    }
});

//only used if auth is enabled
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

function loadSong(songId, requestId, startTime) {
    if (songId.charAt(0) !== '/') {
        useYoutubePlayer = true;
        player.loadVideoById({
            'videoId': songId,
            'startSeconds': startTime,
            'suggestedQuality': 'large'
        });
	} else {
		useYoutubePlayer = false;

		var newUrl = 'https://soundcloud.com' + songId;

		soundcloudWidget.bind(SC.Widget.Events.READY, function() {
			soundcloudWidget.load(newUrl);
			soundcloudWidget.unbind(SC.Widget.Events.READY);

			soundcloudWidget.bind(SC.Widget.Events.PLAY, function() {
				if (startTime) soundcloudWidget.seekTo(startTime * 1000);
				soundcloudWidget.unbind(SC.Widget.Events.PLAY);
			});

			soundcloudWidget.bind(SC.Widget.Events.READY, function() {
				soundcloudWidget.play();
			});
		});
	}
	currentlyPlayingRequestId = requestId;
	paused = false;
	soundcloudFinishedSong = false;
	$("#likeArea").hide();
}


function playpause() {
	if (useYoutubePlayer) {
		if(paused) {
			player.playVideo();
		} else {
			player.pauseVideo();
		}
	} else {
        if (paused) {
            soundcloudWidget.play();
        } else {
            soundcloudWidget.pause();
        }
	}
	paused = !paused;
}

function applyVolumeChange(vol) {
    currentVolume = vol;
    //djbot volume should be log scaled so going from 10->20 has a similar effect as 80->90.
    //youtube volume is linear, so 10->20 has a dramatic effect and 80->90 is barely noticeable.
    //so we need to transform from one scale to another. at 0 and 100 they should be equal to each other.
    // so we can plug in 100 for both volumes in this formula and solve for a scaling factor x
    // 100 = e^(100*x)
    // x is 0.04605
    var youtubeVol = Math.pow(Math.E, vol * 0.04605);

	player.setVolume(youtubeVol);

	//soundcloud goes from 0-1 instead of 0-100 like youtube, and it's similarly linearly scaled
    soundcloudWidget.setVolume(youtubeVol / 100);
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


function nextSong(skip) {
    if(waitingOnNext)
        return;

    waitingOnNext = true;

    player.seekTo(99999);
    soundcloudWidget.seekTo(600000);

    if(skip) {
        var maybeSkipped = "skip=true&";
    } else {
        var maybeSkipped = "";
    }

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
        }
    })
}

function loadCurrentSong() {
    waitingOnNext = true;
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
        return;
    }

    try {
        //are we sitting at the end of a video and it's time to load the next?
        if(!(usingAuth && !userToken && !paused)) {
            if(useYoutubePlayer && player && player.getPlayerState() === 0 ) {
                nextSong(false);
                return
            } else if (!useYoutubePlayer && soundcloudFinishedSong) {
                nextSong(false);
                return;
            }
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