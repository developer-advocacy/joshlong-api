create table if not exists yt_channels (
                                           channel_id   varchar(255) not null primary key,
                                           description  text         not null,
                                           published_at timestamp    not null,
                                           title        varchar(255) not null,
                                           fresh        boolean default false
);

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

create table if not exists yt_playlist_videos
(
    primary key (playlist_id, video_id),
    fresh       boolean default false,
    video_id    varchar(255) not null,
    playlist_id varchar(255) not null
);


create table if not exists yt_channel_videos
(
    primary key (channel_id, video_id),
    channel_id varchar(255) not null,
    video_id   varchar(255) not null
);


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

create table if not exists yt_promotion_batches_entries
(
    id        serial primary key,
    scheduled timestamp    not null,
    promoted  timestamp    null,
    video_id  varchar(1000) references yt_videos (video_id),
    batch_id  varchar(255) not null,
    unique (batch_id, scheduled, video_id)
);


create or replace view yt_promotion_batches(batch_id, start_date, stop_date) as
select ypb.batch_id,
       ypb.start_date,
       ypb.stop_date,
       ypb.pc as percent_promoted
from (select names.batch_id,
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

/*
CREATE OR REPLACE FUNCTION calc_rating() RETURNS TRIGGER AS
$body$
BEGIN
    NEW.rating := (((NEW.like_count * 1.0) / (NEW.view_count * 1.0)) * 100.00);
    RETURN NEW;
END;
$body$ LANGUAGE plpgsql;
*/

-- drop trigger if exists calc_rating on yt_videos;
-- create trigger calc_rating before insert or update on yt_videos for each row execute function calc_rating();



