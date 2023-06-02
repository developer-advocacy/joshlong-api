create table if not exists yt_channels
(
    channel_id   varchar(255) not null primary key,
    description  text         not null,
    published_at timestamp    not null,
    title        varchar(255) not null,
    fresh        boolean default false
);
--///
create table if not exists yt_playlists
(
    playlist_id  varchar(255) not null primary key,
    channel_id   varchar(255) not null,
    published_at timestamp    not null,
    title        varchar(255) not null,
    description  text         not null,
    item_count   int          not null default 0,
    fresh        boolean               default false
);
--///
create table if not exists yt_playlist_videos
(
    primary key (playlist_id, video_id),
    fresh       boolean default false,
    video_id    varchar(255) not null,
    playlist_id varchar(255) not null
);
--///
create table if not exists yt_channel_videos
(
    primary key (channel_id, video_id),
    channel_id varchar(255) not null,
    video_id   varchar(255) not null
);

--///

create table if not exists yt_videos
(
    video_id           varchar(255) not null primary key,
    title              varchar(255) not null,
    description        text         not null,
    published_at       timestamp    not null,
    standard_thumbnail varchar(255) not null,
    category_id        int          not null,
    view_count         int          not null default 0,
    favorite_count     int          not null default 0,
    comment_count      int          not null default 0,
    like_count         int          not null default 0,
    fresh              boolean               default false,
    tags               text[],
    -- this syntax only works in postgres 12+
    -- rating             numeric generated always as (( like_count * 1.0  /  view_count * 1.0 ) * 100.00) STORED
    rating             numeric      not null
);

--///

create table if not exists yt_promotion_batches_entries
(
    id        serial primary key,
    scheduled timestamp    not null,
    promoted  timestamp    null,
    video_id  varchar(1000) references yt_videos (video_id),
    batch_id  varchar(255) not null,
    unique (batch_id, scheduled, video_id)
);
--///
create or replace view yt_promotion_batches(batch_id, start_date, stop_date) as
SELECT ypb.batch_id,
       ypb.start_date,
       ypb.stop_date,
       ypb.pc as percent_promoted
FROM (SELECT names.batch_id,
             (select 100 * ((1.0 * (select count(*)
                                    from yt_promotion_batches_entries e
                                    where e.promoted is not null
                                      and e.batch_id = names.batch_id))
                 /
                            (1.0 *
                             (select count(*)
                              from yt_promotion_batches_entries e
                              where e.batch_id = names.batch_id)))) pc,
             (SELECT min(e.scheduled) AS min
              FROM yt_promotion_batches_entries e
              WHERE e.batch_id::text = names.batch_id::text) AS     start_date,
             (SELECT max(e.scheduled) AS max
              FROM yt_promotion_batches_entries e
              WHERE e.batch_id::text = names.batch_id::text) AS     stop_date

      FROM (SELECT e.batch_id
            FROM yt_promotion_batches_entries e
            GROUP BY e.batch_id) names) ypb
--///
-- this is the part where we update the rating we used to use the 'generated always' as functionality
CREATE OR REPLACE FUNCTION calc_rating() RETURNS TRIGGER AS
$body$
BEGIN
    NEW.rating := (((NEW.like_count * 1.0) / (NEW.view_count * 1.0)) * 100.00);
    RETURN NEW;
END;
$body$ LANGUAGE plpgsql;
--///
drop trigger if exists calc_rating on yt_videos;
--///
create trigger calc_rating before insert or update on yt_videos for each row execute function calc_rating();
--///

-- create or replace view yt_channel_playlist_videos as
-- SELECT c.channel_id,
--        p.playlist_id,
--        v.video_id
-- FROM yt_channels c
--          INNER JOIN yt_playlists p
--                     ON (c.channel_id = p.channel_id)
--          INNER JOIN yt_playlist_videos pv
--                     ON (p.playlist_id = pv.playlist_id)
--          INNER JOIN yt_videos v
--                     ON (pv.video_id = v.video_id)
-- UNION
-- select c.channel_id as channel_id,
--        null         as playlist_id,
--        v.video_id   as video_id
-- FROM yt_channel_videos cv
--
--          INNER JOIN
--      yt_channels c
--      ON
--          (cv.channel_id = c.channel_id)
--          INNER JOIN
--      yt_videos v
--      ON
--          (cv.video_id = v.video_id)
-- where v.video_id not in (select vv.video_id from yt_playlist_videos vv);
--
--
-- create or replace view yt_channel_playlist_videos_full as
-- select cpv.playlist_id,
--        v.video_id,
--        cpv.channel_id,
--        greatest(0, (((v.like_count * 1.0) / (v.view_count * 1.0)))) * 100         as liked_percentage,
--        (select p.title from yt_playlists p where p.playlist_id = cpv.playlist_id) as playlist_title,
--        (select c.title from yt_channels c where c.channel_id = cpv.channel_id)    as channel_title,
--        v.title                                                                    as video_title,
--        v.view_count,
--        v.like_count,
--        v.comment_count,
--        v.category_id,
--        v.published_at,
--        v.tags,
--        v.description
-- from yt_channel_playlist_videos cpv,
--      yt_videos v
-- where v.video_id = cpv.video_id;
--
