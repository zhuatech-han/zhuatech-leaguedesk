-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
-- 空库仅建结构；业务资料经真实界面或接口录入。

create table season (
 id bigint auto_increment primary key,
 department_id bigint not null,
 name varchar(120) not null,
 enabled boolean not null,
 version bigint not null,
 foreign key (department_id) references department(id),
 unique(department_id,name)
);

create table court (
 id bigint auto_increment primary key,
 department_id bigint not null,
 name varchar(120) not null,
 enabled boolean not null,
 version bigint not null,
 foreign key (department_id) references department(id),
 unique(department_id,name)
);

create table tournament (
 id bigint auto_increment primary key,
 department_id bigint not null,
 season_id bigint not null,
 owner_id bigint not null,
 name varchar(120) not null,
 category varchar(60) not null,
 format varchar(20) not null,
 status varchar(20) not null,
 group_count int not null,
 advance_count int not null,
 best_of int not null,
 capacity int not null,
 published boolean not null,
 starts_at timestamp(6) not null,
 ends_at timestamp(6) not null,
 description varchar(2000) not null,
 version bigint not null,
 foreign key (department_id) references department(id),
 foreign key (season_id) references season(id),
 foreign key (owner_id) references account(id),
 index tournament_org(department_id,status,starts_at)
);

create table entry (
 id bigint auto_increment primary key,
 tournament_id bigint not null,
 account_id bigint not null,
 name varchar(120) not null,
 status varchar(20) not null,
 checked_in boolean not null,
 seed int not null,
 group_no int not null,
 note varchar(600) not null,
 version bigint not null,
 foreign key (tournament_id) references tournament(id),
 foreign key (account_id) references account(id),
 unique(tournament_id,account_id),
 unique(tournament_id,name)
);

create table game (
 id bigint auto_increment primary key,
 tournament_id bigint not null,
 stage varchar(20) not null,
 round_no int not null,
 position int not null,
 group_no int not null,
 home_id bigint,
 away_id bigint,
 source_home_id bigint,
 source_away_id bigint,
 winner_id bigint,
 status varchar(20) not null,
 court_id bigint,
 referee_id bigint,
 starts_at timestamp(6),
 ends_at timestamp(6),
 home_score int not null,
 away_score int not null,
 result_type varchar(20) not null,
 version bigint not null,
 foreign key (tournament_id) references tournament(id),
 foreign key (home_id) references entry(id),
 foreign key (away_id) references entry(id),
 foreign key (source_home_id) references game(id),
 foreign key (source_away_id) references game(id),
 foreign key (winner_id) references entry(id),
 foreign key (court_id) references court(id),
 foreign key (referee_id) references account(id),
 unique(tournament_id,stage,round_no,position,group_no),
 index game_schedule(court_id,starts_at,ends_at),
 index game_referee(referee_id,starts_at,ends_at)
);

create table league_event (
 id bigint auto_increment primary key,
 tournament_id bigint not null,
 actor_id bigint not null,
 action varchar(60) not null,
 details varchar(2000) not null,
 created_at timestamp(6) not null,
 foreign key (tournament_id) references tournament(id),
 foreign key (actor_id) references account(id),
 index league_event_tournament(tournament_id,id)
);

create table command_stamp (
 id bigint auto_increment primary key,
 request_id varchar(36) not null unique,
 actor_id bigint not null,
 fingerprint varchar(64) not null,
 tournament_id bigint not null,
 foreign key (actor_id) references account(id),
 foreign key (tournament_id) references tournament(id)
);
