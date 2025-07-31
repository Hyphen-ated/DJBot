//// Load the IFrame Player API code asynchronously.
//var tag = document.createElement('script');
//tag.src = "https://www.youtube.com/player_api";
//var firstScriptTag = document.getElementsByTagName('script')[0];
//firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
//
//// Replace the 'ytplayer' element with an <iframe> and
//// YouTube player after the API code downloads.
//var player;
//function onYouTubePlayerAPIReady() {
//  player = new YT.Player('player', {
//    width: '600',
//    height: '400',
//    videoId: 'JidJV1ue1lI',
//    events: {
//        'onError': onYoutubeError,
//        'onReady': onYoutubeReady
//    },
//    playerVars: {
//        fs: 0,
//        iv_load_policy: 3,
//        rel: 0
//    }
//  });
//}
//
//
//function onYoutubeError(event) {
//    console.log("youtube error:");
//    console.log(event);    
//    if(event.data == 100 || event.data == 101 || event.data == 150) {
//        //not found, or not allowed to be embedded. hopefully the dj server checked for this, but if not, we skip it now.
//
//        nextSong(true);
//    }
//    
//}
//
//function onYoutubeReady(event) {
//    console.log("youtube became ready");
//    youtubeBecameReady = true;
//}


      // 2. This code loads the IFrame Player API code asynchronously.
      var tag = document.createElement('script');

      tag.src = "https://www.youtube.com/iframe_api";
      var firstScriptTag = document.getElementsByTagName('script')[0];
      firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

      // 3. This function creates an <iframe> (and YouTube player)
      //    after the API code downloads.
      var player;
      function onYouTubeIframeAPIReady() {
        console.log("onYouTubeIframeAPIReady");
        player = new YT.Player('player', {
          height: '790',
          width: '940',
          videoId: 'M7lc1UVf-VE',
          events: {
            'onReady': onPlayerReady,
            'onStateChange': onPlayerStateChange
          }
        });
      }

      // 4. The API will call this function when the video player is ready.
      function onPlayerReady(event) {
        console.log("onPlayerReady");
        event.target.playVideo();
      }

      // 5. The API calls this function when the player's state changes.
      //    The function indicates that when playing a video (state=1),
      //    the player should play for six seconds and then stop.
      var done = false;
      function onPlayerStateChange(event) {
        console.log("onPlayerStateChange");
        if (event.data == YT.PlayerState.PLAYING && !done) {
          setTimeout(stopVideo, 6000);
          done = true;
        }
      }
      function stopVideo() {
        player.stopVideo();
      }
