-- 创建库
create database if not exists hanamizuki;

-- 切换库
use hanamizuki;

-- ---------------------------------------------------------------------------
-- 约定
--   1. 主键统一 bigint auto_increment
--   2. 主实体表带 isDelete 逻辑删除；关系表 / 行为表硬删除
--   3. 不建外键，一致性由应用层保证，只建查询需要的索引
--   4. 时间统一 datetime，业务时间单独命名（takenTime / openTime），
--      不要和 createTime / updateTime 混用
-- ---------------------------------------------------------------------------


-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码（BCrypt）',
    lineUserId   varchar(256)                           null comment 'LINE Login 用户 id（预留，暂未接入）',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    unique key uk_userAccount (userAccount),
    index idx_lineUserId (lineUserId)
) comment '用户' collate = utf8mb4_unicode_ci;


-- 好友关系表（硬删除）
-- 通过后写入双向两行，查好友列表只需 where userId = ? and status = 1，单列查询
-- 待通过阶段只有 申请人 -> 目标 这一行
create table if not exists friend
(
    id            bigint auto_increment comment 'id' primary key,
    userId        bigint                             not null comment '用户 id',
    friendId      bigint                             not null comment '好友 id',
    status        tinyint  default 0                 not null comment '状态：0-待通过 1-已通过',
    requestUserId bigint                             not null comment '发起申请的用户 id',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_userId_friendId (userId, friendId),
    index idx_userId (userId),
    index idx_friendId (friendId)
) comment '好友关系' collate = utf8mb4_unicode_ci;


-- 照片表
-- 一张照片可以出现在多个相册里，所以这里只放照片本身的属性，
-- 「这张照片在这个相册里的说明 / 涂鸦」放 album_photo
create table if not exists photo
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                             not null comment '上传用户 id',
    filePath   varchar(1024)                      not null comment '原图存储路径',
    thumbPath  varchar(1024)                      null comment '缩略图存储路径',
    picWidth   int                                null comment '图片宽度',
    picHeight  int                                null comment '图片高度',
    picSize    bigint                             null comment '图片体积（字节）',
    picFormat  varchar(32)                        null comment '图片格式：jpeg/png',
    takenTime  datetime                           null comment '拍摄时间（EXIF DateTimeOriginal，取不到则回落为上传时间）',
    latitude   decimal(10, 7)                     null comment '纬度（EXIF GPS）',
    longitude  decimal(10, 7)                     null comment '经度（EXIF GPS）',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_userId_takenTime (userId, takenTime)
) comment '照片' collate = utf8mb4_unicode_ci;


-- 相册表
create table if not exists album
(
    id           bigint auto_increment comment 'id' primary key,
    title        varchar(512)                       null comment '相册标题（AI 生成，可改）',
    summary      varchar(1024)                      null comment '一句话总结（AI 生成）',
    coverPhotoId bigint                             null comment '封面照片 id（AI 挑选，可改）',
    albumDate    date                               null comment '相册日期（取自照片簇）',
    place        varchar(256)                       null comment '地点（取自照片簇）',
    aiGenerated  tinyint  default 0                 not null comment '文案是否由 AI 生成：0-规则兜底 1-AI 生成',
    userId       bigint                             not null comment '创建用户 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId)
) comment '相册' collate = utf8mb4_unicode_ci;


-- 相册成员表（硬删除）
create table if not exists album_member
(
    id         bigint auto_increment comment 'id' primary key,
    albumId    bigint                                not null comment '相册 id',
    userId     bigint                                not null comment '用户 id',
    memberRole varchar(64) default 'editor'          not null comment '成员角色：owner/editor',
    createTime datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_albumId_userId (albumId, userId),
    index idx_albumId (albumId),
    index idx_userId (userId)
) comment '相册成员' collate = utf8mb4_unicode_ci;


-- 相册照片表
-- 关键：说明文字、排序、涂鸦层属于「这张照片在这个相册里」，不属于照片本身。
-- 同一张照片在两个相册里画不同的涂鸦是正常需求，所以这张表不是纯关联表。
-- 画面 8 的六行元数据（場所/時間/音楽/コメント/メンバー/天気）除了時間和メンバー，都落在这里。
create table if not exists album_photo
(
    id                bigint auto_increment comment 'id' primary key,
    albumId           bigint                             not null comment '相册 id',
    photoId           bigint                             not null comment '照片 id',
    position          int      default 0                 not null comment '在相册内的排序',
    caption           varchar(256)                       null comment '照片下方短标签，如「放課後」（AI 生成）',
    place             varchar(256)                       null comment '場所（GPS 逆地理，取不到则 AI 看图推断）',
    weather           varchar(64)                        null comment '天気：晴れ/曇り/雨/雪（AI 看图判断）',
    photoComment      varchar(1024)                      null comment 'コメント（AI 生成，用户可改）',
    music             varchar(512)                       null comment '音楽（只能用户手填，无自动来源）',
    overlayPath       varchar(1024)                      null comment '手写涂鸦层 PNG 路径（透明底，与原图同比例）',
    compositePath     varchar(1024)                      null comment '合成图路径（原图+涂鸦，派生数据，overlay 更新时失效）',
    overlayUserId     bigint                             null comment '最后画涂鸦的用户 id',
    overlayUpdateTime datetime                           null comment '涂鸦最后更新时间',
    createTime        datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime        datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete          tinyint  default 0                 not null comment '是否删除',
    unique key uk_albumId_photoId (albumId, photoId),
    index idx_albumId (albumId),
    index idx_photoId (photoId)
) comment '相册照片' collate = utf8mb4_unicode_ci;


-- 相册分享表
-- shareToken 必须随机不可猜，不能用自增 id —— 分享页是公开的，能猜到 id 就能翻别人的相册
create table if not exists album_share
(
    id         bigint auto_increment comment 'id' primary key,
    shareToken varchar(64)                        not null comment '分享 token（随机 32 位）',
    albumId    bigint                             not null comment '相册 id',
    userId     bigint                             not null comment '创建分享的用户 id',
    revokeTime datetime                           null comment '撤销时间，非空表示已撤销（访问返回 410）',
    viewCount  int      default 0                 not null comment '浏览次数',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_shareToken (shareToken),
    index idx_albumId (albumId)
) comment '相册分享' collate = utf8mb4_unicode_ci;


-- 时间胶囊表
-- openTime 之前，接口不得返回 message 和相册内容。这个判断必须在服务端做，
-- 前端藏起来不算实现了这个功能。
create table if not exists capsule
(
    id           bigint auto_increment comment 'id' primary key,
    albumId      bigint                             not null comment '相册 id',
    userId       bigint                             not null comment '创建用户 id',
    capsuleMsg   varchar(2048)                      null comment '给未来的自己的留言（封存期间不得下发）',
    openTime     datetime                           not null comment '可开启时间',
    openedTime   datetime                           null comment '实际开启时间，非空表示已开启',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_albumId (albumId)
) comment '时间胶囊' collate = utf8mb4_unicode_ci;


-- 成册任务表（硬删除）
-- AI 成册是异步的，且一次请求可能产出多个相册（30 张照片横跨两个下午 = 两个相册），
-- 所以客户端轮询的是任务而不是相册
create table if not exists album_job
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                             not null comment '发起用户 id',
    photoIds   varchar(4096)                      not null comment '入参照片 id 列表（json 数组）',
    status     varchar(32)                        not null comment '状态：PENDING/CLUSTERING/ENRICHING/READY/FAILED',
    albumIds   varchar(1024)                      null comment '产出的相册 id 列表（json 数组）',
    errorMsg   varchar(1024)                      null comment '失败原因',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_userId (userId)
) comment '成册任务' collate = utf8mb4_unicode_ci;
