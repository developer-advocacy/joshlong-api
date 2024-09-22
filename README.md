# Blog API


This application is an API for the various functions of my blog. It is backed by a Git repository and provides a Lucene index. 

## Outstanding Things to Do 

* figure out how to configure the CORS hosts with properties.
* figure out how to configure the `/media` API host for the rewrite that happens in `MediaRestController`
* figure out how to configure the Github Webhook for whenever the git repository is changed and the index needs to be rebuilt
* figure out pagination for the blog.
* figure out monthly links or a dropdown so I can see all blogs from january 2012, or something like that?


## Some Useful Queries for Debugging 

If you want to examine what the GraphQL engine knows about your types, you might issue the following query, 
which will enumerate things like the types, their fields, and their fields' names. 

``` 
{
  __schema {
    types {
      name
      fields {
        name
        type {
          name
          kind
        }
      }
    }
  }
}

```

## If Something is Invalid 

All of the following things have externalities that need to be managed. Once they're updated, something, somewhere, needs to then trigger a rebuild using this API's rebuild functionality. Ideally, all these tributary repositories would in turn trigger rebuilds, but they don't. So, if in doubt, just bump any random file in the [repository containing the content for joshlong.com](https://github.com/joshlong/joshlong.github.io-content). This is sure to trigger a rebuild. 

### Not Seeing the Latest Blog Posts? 

Check that you've committed [a valid file to the content repository](https://github.com/joshlong/joshlong.github.io-content). 
That in turn should kick off a Github Actions run that then triggers a rebuild on the API and that invalidates all cached state and reads everything anew.

### Not Seeing the Latest Spring Tips Live Episodes? 

Make sure that [the `spring-tips/site-generator` Github Action has run](https://github.com/spring-tips/site-generator/tree/master). It might be that the Github Action is expired and I need to manually bump it. This simply updates the [`episodes.json` file in this git repository](https://github.com/spring-tips/spring-tips.github.io). You'll see updates [on the SpringTipsLive.io `episodes.json` feed](https://springtipslive.io/episodes.json). 

TODO: there should be a Github Action on the repository with `episodes.json` that in turn triggers a rebuild of joshlong.com. That might be seen as an uncooth coupling of concerns, however. Do I really want the Spring Tips Live services to be aware of their representation on JoshLong.com?  

### Not Seeing the Latest Bootiful Podcast episode? 




### Not Seeing the Latest Appearances? 

Make sure the `appearances.json` feed has been updated. It may fail to do that if you haven't run [this appearances-processor](https://github.com/developer-advocacy/appearances-processor/actions/workflows/pythonapp.yml). Sometimes Github Actions needs a bump. Note that the content in the appearances carousel are not markdown-compliant. 


# Youtube Ingest Job

This job ingests data about videos on YouTube into a PostgreSQL Database.

## Running Locally

I use the following Docker image to get a local database to which I can connect:

```shell
#!/usr/bin/env bash
NAME=${1:-default}-postgres
PORT=${2:-5432}
docker ps -aq -f "name=${NAME}" | while read  l; do docker rm -f $l ; echo "stopping $l"; done 
docker run --name  $NAME  \
	-p ${PORT}:5432 \
	-e POSTGRES_USER=user \
	-e PGUSER=user \
	-e POSTGRES_PASSWORD=pw \
	postgres:latest
```

Then I can connect to that PostgreSQL database instance with `psql` on my local machine:

```shell
PGPASSWORD=pw psql -U user -h localhost user  
```

Alternatively, ive supplied a `docker-compose.yaml` file: `docker compose up`

### You'll Need an API Key

Follow the steps below to create an API Key. Go to Google Cloud Console, then to the APIs & Services section, find Youtube Data API. Enable it. Create a new Credential. Choose the one that results _not_ in an OAuth client but an API key. 


## To Do
* create some sort of reporting (SQL views, anyone?) for the most liked videos so we can then use that figure out what to tweet, whats popular, what to invest more in, etc.

## Ingest the YouTube Data

Most of the code in this module - `com.joshlong.youtube.client` - provides a reactive HTTP client to the YouTube Data HTTP APIs. It provides methods to query various aspects of the API based on the usecase supported by this module: read in and store all the data in a YouTube channel (like the [`@SpringSourceDev` YouTube channel](https://www.youtube.com/user/springsourcedev)).


### Tables

This part of the system stores everything in the following tables:

* `yt_videos` - this table shows everything about the videos themselves, their like count, views, title, description, ID, etc.
* `yt_channels` - this lists the channel we start our search with as well as all channels discovered in playlists. It's possible for one channel to have playlists that incorporate videos from other channels. So even if you start with one channel, you might get many channels here in the end.
* `yt_channel_videos` - this is a join table of channels and videos.
* `yt_playlists` - this shows all the playlists discovered in a given channel
* `yt_playlist_videos` - this is a join table for the playlists and videos

### Views

There are a lot of joins implied in making this work, so to make it easier to query, the following SQL views exist.

* `yt_channel_playlist_videos` - this view is just a join table showing the `video_id`, `channel_id`, and `playlist_id` (if one exists). Videos without a `playlist_id` are assumed to be in the default, unspecified, _uploads_ playlist.
* `yt_channel_playlist_videos_full` - this view enriches the first view, pulling in details like the video title, channel title, the various metrics about the videos, the playlist title, etc.

## Scheduling Tweets for Promotion

The second part of this job has to do with tweeting out videos people might like. We do this by ranking the videos by how _liked_ they are (the _like count_ as a percentage of the _view count_) and then tweeting out a new video each week. We don't want to tweet out the same video every week, though, so we need some way of keeping track of which videos have recently been tweeted and which haven't. The core of that

The following query finds all the Spring Tips videos:

```sql
select video_id, now()
 from yt_channel_playlist_videos_full
 where playlist_id = (select playlist_id from yt_playlists where title = 'Spring Tips')
 order by liked_percentage desc;
```

the idea is that we'll seed a table, `yt_promoted_videos`, with all the videos from the `yt_channel_playlist_videos_full`, ranking the most popular to least popular using the query you just saw. So that query is important.

### Resetting the Promoted Tweets Table
Reset the `yt_promoted_tweets` table thusly:

```sql
truncate yt_promoted_videos restart identity
```

Then populate the table with the new schedule:

```sql
insert into yt_promoted_videos(video_id)
    select video_id
    from yt_channel_playlist_videos_full
    where playlist_id = (select playlist_id from yt_playlists where title = 'Spring Tips')
    order by liked_percentage desc;
```

The problem is that we'll often have to re-run this when a new video is added to the channel. But we don't want to throw out the old schedule. We need to update the above query to be idempotent, inserting new records but doing nothing if the record already exists.

Here's my first attempt at that.

```sql
insert into yt_promoted_videos(video_id)
    select video_id
    from yt_channel_playlist_videos_full
    where playlist_id = (select playlist_id from yt_playlists where title = 'Spring Tips')
order by liked_percentage desc
 ON CONFLICT (video_id)  do UPDATE SET priority_no = EXCLUDED.priority_no;

```

-- todo we need to figure out how to update the priority_no to the new value we have computed


To figure out what tweet needs our attention next, do this:

```sql
select video_id from yt_promoted_videos where priority_no = 
  (select min (priority_no) from yt_promoted_videos where date is null );
```

That'll give us the next `video_id` to focus on. Then we will tweet it out, and update `yt_promoted_videos` to have a non-null `date` for that row. Here's how that query would look:

```sql
update yt_promoted_videos set date =NOW() where video_id = ? 
```

### When to Reset

There are two situations in which we'll need to reset or update the `yt_promoted_videos` table.

The first situation is where we've added a new video (perhaps somebody uploaded a new one? I do this often for Spring Tips) and that video isn't reflected in the `yt_promoted_videos` table. We can determine this to be the case if the following query returns any number but `0`.

```sql
select count(*) from yt_channel_playlist_videos_full v 
    where
        v.playlist_id = (select p.playlist_id from yt_playlists p where p.title = 'Spring Tips') and
        v.video_id not in (select p.video_id from yt_promoted_videos p ) ;
```

New videos are added regularly (often a few times a week), so it doesn't make any sense to reset the schedule each time there's a new video. We need to insert the new record in the forecast, and as long as it's got a null value for the `date`, it'll be picked up on the next tweet run.

The second situation is where we've got no more tweets to schedule - all the `date` columns are non-`null`. We can determine this to be the case with the following query:

```sql
select count(*) from yt_promoted_videos where date is null 
```

If this query returns `0`, then we need to reset the table completely using the queries in the `Resetting the Promoted Tweets Table` section above. These will delete everything in the table and reset the identity column and start from zero again.

## Deployment

This program needs to talk to Youtube to do its work. You'll need to provision and specify a Youtube Data key.

This also needs to [talk to the Twitter service](https://twitter.developeradvocacy.services), so make sure to specify the correct RabbitMQ keys. They're deployed as part of the organizational secrets for the Github organization.

## To Do:

* the analytics should schedule a tweet promoting a video. the code is there, but it's not yet integrated into the analytics and promotion logic
* update the logic for `yt_promoted_videos`. it should include the name of the playlist in the results, maybe? or maybe we should have that as a separate table? right now it's tied to `spring tips`
* https://www.educba.com/postgresql-materialized-views/ turn the `yt_promoted_videos` table into a postgresql materialized view and simply call  `REFRESH MATERIALIZED VIEW CONCURRENTLY view_name  `.
