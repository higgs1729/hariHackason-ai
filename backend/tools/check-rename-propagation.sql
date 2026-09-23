-- Which copies of a user's name moved when they renamed themselves.
--
-- Run check-rename-propagation.py first, then this. Four columns follow the
-- source and four are snapshots; the DDL says which is which, and this is the
-- only thing that checks the code agrees.
--
--   follow   -> matched must equal rows
--   snapshot -> matched must be 0
set @uid = (select id from user where userAccount = 'nao');
set @name = (select userName from user where id = @uid);

select 'follow' kind, 'friend.friendUserName' col,
       count(*) rows_, ifnull(sum(friendUserName <=> @name), 0) matched
from friend where friendId = @uid
union all
select 'follow', 'album_member.userName',
       count(*), ifnull(sum(userName <=> @name), 0)
from album_member where userId = @uid
union all
select 'follow', 'photo.userName',
       count(*), ifnull(sum(userName <=> @name), 0)
from photo where userId = @uid
union all
select 'follow', 'album.userName',
       count(*), ifnull(sum(userName <=> @name), 0)
from album where userId = @uid
union all
select 'snapshot', 'album_photo.overlayUserName',
       count(*), ifnull(sum(overlayUserName <=> @name), 0)
from album_photo where overlayUserId = @uid
union all
select 'snapshot', 'notification.fromUserName',
       count(*), ifnull(sum(fromUserName <=> @name), 0)
from notification where fromUserId = @uid
union all
select 'snapshot', 'capsule_recipient.userName',
       count(*), ifnull(sum(userName <=> @name), 0)
from capsule_recipient where userId = @uid
union all
select 'snapshot', 'block.blockedUserName',
       count(*), ifnull(sum(blockedUserName <=> @name), 0)
from block where blockedUserId = @uid;
