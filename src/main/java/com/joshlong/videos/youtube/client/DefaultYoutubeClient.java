package com.joshlong.videos.youtube.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.joshlong.utils.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

import static com.joshlong.videos.youtube.client.DefaultYoutubeClient.JsonFormattingUtils.*;

class DefaultYoutubeClient implements YoutubeClient {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final RestClient http;

	private final String apiKey;

	DefaultYoutubeClient(RestClient http, String apiKey) {
		this.http = http;
		this.apiKey = apiKey;
	}

	@Override
	public Channel getChannelByUsername(String username) {
		return findChannel("&forUsername={username}", Map.of("username", username));
	}

	@Override
	public Channel getChannelById(String channelId) {
		return findChannel("&id={channelId}", Map.of("channelId", channelId));
	}

	/**
	 * todo we need to decompose this a little: to get a channel for a username. a method
	 * to get uploads playlist for a channel. to get all videos from a playlist.
	 * <p>
	 * This returns all the videos for a given channel
	 * @param username the username whose channel content we want to find
	 * @return all the videos
	 */
	@Override
	public Collection<Video> getAllVideosByUsernameUploads(String username) {
		// https://stackoverflow.com/questions/18953499/youtube-api-to-fetch-all-videos-on-a-channel/27872244#27872244
		// this solution has a low quota cost and seems to truly return <em>all</em> the
		// videos
		var playlistForChannel = "https://www.googleapis.com/youtube/v3/channels?part=contentDetails&forUsername={user}&key={key}";
		var jsonNode = this.http//
				.get()//
				.uri(playlistForChannel, Map.of("user", username, "key", this.apiKey))//
				.retrieve()//
				.body(JsonNode.class);//

		var uploadsPlaylistId = jsonNode//
				.get("items")//
				.get(0).get("contentDetails")//
				.get("relatedPlaylists")//
				.get("uploads")//
				.textValue();

		return getAllVideosByPlaylist(uploadsPlaylistId);

	}

	private Video buildVideoFromJsonNode(JsonNode item) {
		var id = item.get("id").textValue();
		var snippet = item.get("snippet");
		var channelId = snippet.get("channelId").textValue();
		var publishedAt = buildDateFrom(snippet.get("publishedAt").textValue());
		var description = snippet.get("description").textValue();
		var title = snippet.get("title").textValue();
		var thumbnailUrl = UrlUtils.url(snippet.get("thumbnails").get("default").get("url").textValue());
		var tags = jsonNodeOrNull(snippet, "tags");
		var upcoming = snippet.get("liveBroadcastContent").textValue() != null
				&& snippet.get("liveBroadcastContent").textValue().contains("upcoming");
		var statistics = item.get("statistics");
		var viewCount = numberOrZero(statistics, "viewCount");
		var likeCount = numberOrZero(statistics, "likeCount");
		var favCount = numberOrZero(statistics, "favoriteCount");
		var commentCount = numberOrZero(statistics, "commentCount");
		var categoryId = Integer.parseInt(snippet.get("categoryId").textValue());
		var tagsList = new ArrayList<String>();
		if (null != tags)
			for (var tag : tags)
				tagsList.add(tag.textValue());
		return new Video(id, title, description, publishedAt, thumbnailUrl, tagsList, categoryId, viewCount, likeCount,
				favCount, commentCount, channelId, upcoming);
	}

	@Override
	public Map<String, Video> getVideosByIds(List<String> videoIds) {
		var joinedIds = String.join(",", videoIds);
		var url = "https://youtube.googleapis.com/youtube/v3/videos?part={parts}&id={ids}&key={key}";
		var jn = this.http.get()//
				.uri(url, Map.of("ids", joinedIds, "key", this.apiKey, "parts", "snippet,statistics"))//
				.retrieve()//
				.body(JsonNode.class);//
		var items = jn.get("items");
		var list = new ArrayList<Video>();
		for (var item : items)
			list.add(buildVideoFromJsonNode(item));
		var map = new HashMap<String, Video>();
		for (var m : list)
			map.put(m.videoId(), m);
		return map;
	}

	@Override
	public Video getVideoById(String videoId) {
		var map = this.getVideosByIds(List.of(videoId));

		if (!map.isEmpty())
			return map.get(videoId);

		throw new IllegalArgumentException("No video with id " + videoId + " found");
	}

	private Playlist buildPlaylistForJsonNode(JsonNode jsonNode) {
		var itemCount = jsonNode.get("contentDetails").get("itemCount").intValue();
		var playlistId = jsonNode.get("id").textValue();
		var snippet = jsonNode.get("snippet");
		var title = snippet.get("title").textValue();
		var description = snippet.get("description").textValue();
		var publishedAt = buildDateFrom(snippet.get("publishedAt").textValue());
		var channelId = snippet.get("channelId").textValue();
		return new Playlist(playlistId, channelId, publishedAt, title, description, itemCount);
	}

	@Override
	public Collection<Video> getAllVideosByPlaylist(String playlistId) {
		var playlistVideos = this.getVideosByPlaylist(playlistId, null);//
		var nextPageToken = playlistVideos.nextPageToken();
		if (!StringUtils.hasText(nextPageToken)) {
			return new ArrayList<>();
		}
		else {
			return this.getVideosByPlaylist(playlistId, nextPageToken).videos();
		}
	}

	@Override
	public PlaylistVideos getVideosByPlaylist(String playlistId, String pageToken) {

		var url = "https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&key={key}&maxResults=500&playlistId={playlistId}"
				+ (StringUtils.hasText(pageToken) ? "&pageToken={pt}" : "");
		var jsonNode = this.http.get()//
				.uri(url, Map.of("key", this.apiKey, "pt", pageToken + "", "playlistId", playlistId))//
				.retrieve()//
				.body(JsonNode.class);//

		var items = jsonNode.get("items");
		var list = new ArrayList<String>();
		for (var item : items) {
			list.add(item.get("contentDetails").get("videoId").textValue());
		}
		var videoCollection = getVideosByIds(list).values();//
		var pageInfo = jsonNode.get("pageInfo");
		var resultsPerPage = pageInfo.get("resultsPerPage").intValue();
		var totalResults = pageInfo.get("totalResults").intValue();
		var nextPageToken = stringOrNull(jsonNode, "nextPageToken");
		var prevPageToken = stringOrNull(jsonNode, "prevPageToken");
		return new PlaylistVideos(playlistId, videoCollection, nextPageToken, prevPageToken, resultsPerPage,
				totalResults);
	}

	@Override
	public Collection<Video> getAllVideosByChannel(String channelId) {
		var playlistVideos = this.getVideosByChannel(channelId, null);//
		var nextPageToken = playlistVideos.nextPageToken();
		if (StringUtils.hasText(nextPageToken)) {
			return getVideosByChannel(channelId, nextPageToken).videos();
		}
		return new ArrayList<>();
	}

	@Override
	public Playlist getPlaylistById(String playlistId) {
		var url = "https://youtube.googleapis.com/youtube/v3/playlists?part=snippet,contentDetails&id={id}&key={key}";
		var json = this.http.get()//
				.uri(url, Map.of("id", playlistId, "key", this.apiKey))//
				.retrieve()//
				.body(JsonNode.class);//
		var first = json.get("items").get(0);
		var snippet = first.get("snippet");
		return new Playlist(first.get("id").textValue(), //
				snippet.get("channelId").textValue(), //
				buildDateFrom(snippet.get("publishedAt").textValue()), //
				snippet.get("title").textValue(), //
				snippet.get("description").textValue(), //
				first.get("contentDetails").get("itemCount").intValue()//
		);

	}

	@Override
	public ChannelVideos getVideosByChannel(String channelId, String pageToken) {
		var url = "https://www.googleapis.com/youtube/v3/search?channelId={channelId}&order=date&part=snippet&type=video&maxResults=50&key={key}"
				+ (StringUtils.hasText(pageToken) ? "&pageToken={pt}" : "");
		var jn = this.http.get().uri(url, Map.of("key", this.apiKey, "channelId", channelId, "pt", "" + pageToken))
				.retrieve()//
				.body(JsonNode.class);//
		var nextPageToken = stringOrNull(jn, "nextPageToken");
		var prevPageToken = stringOrNull(jn, "prevPageToken");
		var items = jsonNodeOrNull(jn, "items");
		Assert.isTrue(items != null, () -> "there should be 1 or more videos returned!");
		var results = new ArrayList<String>();
		for (var video : items) {
			results.add(video.get("id").get("videoId").textValue());
		}
		log.info("there are {} results", results.size());
		var map = this.getVideosByIds(results);
		return new ChannelVideos(channelId, map.values(), nextPageToken, prevPageToken);
	}

	private Channel findChannel(String urlExtension, Map<String, String> params) {
		var uri = "https://youtube.googleapis.com/youtube/v3/channels?part=snippet,contentDetails&key={key}"
				+ urlExtension;
		var uriVariables = new HashMap<String, String>();
		uriVariables.put("key", this.apiKey);
		uriVariables.putAll(params);
		var jn = this.http//
				.get()//
				.uri(uri, uriVariables)//
				.retrieve()//
				.body(JsonNode.class);//
		return buildChannelFromJsonNode(jn);
	}

	@Override
	public ChannelPlaylists getPlaylistsByChannel(String channelId, String pageToken) {
		var url = "https://youtube.googleapis.com/youtube/v3/playlists?part=id,status,snippet,contentDetails&channelId={channelId}&maxResults=50&key={key}"
				+ (StringUtils.hasText(pageToken) ? "&pageToken={pt}" : "");
		var jsonNode = this.http.get()//
				.uri(url, Map.of("channelId", channelId, "key", this.apiKey, "pt", "" + pageToken))//
				.retrieve()//
				.body(JsonNode.class);//
		var tr = jsonNode.get("pageInfo").get("totalResults").intValue();
		var nextPageToken = stringOrNull(jsonNode, "nextPageToken");
		var prevPageToken = stringOrNull(jsonNode, "prevPageToken");
		var list = new ArrayList<Playlist>();
		var items = jsonNode.get("items");
		for (var i : items)
			list.add(buildPlaylistForJsonNode(i));
		return new ChannelPlaylists(channelId, list, tr, prevPageToken, nextPageToken);
	}

	@Override
	public Collection<Playlist> getAllPlaylistsByChannel(String channelId) {
		var channelPlaylists = this.getPlaylistsByChannel(channelId, null);//
		var nextPageToken = channelPlaylists.nextPageToken();
		if (!StringUtils.hasText(nextPageToken)) {
			return new ArrayList<>();
		} //
		else {
			return this.getPlaylistsByChannel(channelId, nextPageToken).playlists();
		}
	}

	private Channel buildChannelFromJsonNode(JsonNode jsonNode) {
		var items = jsonNode.get("items");
		for (var i : items) {
			Assert.isTrue(i.get("kind").textValue().equals("youtube#channel"), "the item is a YouTube channel");
			var id = i.get("id").textValue();
			var title = i.get("snippet").get("title").textValue();
			var description = i.get("snippet").get("description").textValue();
			var publishedAt = i.get("snippet").get("publishedAt").textValue();

			return new Channel(id, title, description, buildDateFrom(publishedAt));
		}
		throw new RuntimeException("we should never reach this point! there was no Channel found");
	}

	static class JsonFormattingUtils {

		static int numberOrZero(JsonNode node, String propertyName) {
			var json = jsonNodeOrNull(node, propertyName);
			return json != null ? Integer.parseInt(json.textValue()) : 0;
		}

		static Date buildDateFrom(String isoDate) {
			return Date.from(Instant.parse(isoDate));
		}

		static JsonNode jsonNodeOrNull(JsonNode node, String propertyName) {
			return node.has(propertyName) ? node.get(propertyName) : null;
		}

		static String stringOrNull(JsonNode jsonNode, String propertyName) {
			var res = jsonNodeOrNull(jsonNode, propertyName);
			return res != null ? res.textValue() : null;
		}

	}

}
