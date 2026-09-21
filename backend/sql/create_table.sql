-- 创建库
create database if not exists hanamizuki;

-- 切换库
use hanamizuki;

-- ---------------------------------------------------------------------------
-- 约定
--   1. 主键统一 bigint auto_increment
--   2. 主实体表带 isDelete 逻辑删除；关系表 / 行为表硬删除
--   3. 不建外键，一致性由应用层保证，只建查询需要的索引
--   4. 时间统一 datetime，业务时间单独命名（takenTime / openTime / expireTime），
--      不要和 createTime / updateTime 混用
--   5. 列表型数据存 varchar 里的 json 数组，不拆子表
--
-- 冗余策略
--   列表页和详情页不 join。展示需要的字段一律冗余到当前表，
--   计数一律用冗余列，不用 count(*)。
--
--   代价是一致性，所以每个冗余列都标注了三件事：
--     [源]   谁是权威数据
--     [同步] 什么时候写这个副本
--     [漂移] 不同步会怎样
--
--   同步时机只有两种，不要发明第三种：
--     · 快照   —— 写入时复制一次，之后源变了也不跟（历史留痕，如 album_share）
--     · 跟随   —— 源变更时必须同步更新（如 user.userName → friend.friendUserName）
--
--   漂移的兜底：所有冗余列都能从源重算。/api/dev/rebuild-denorm 全量重算一次，
--   演示前跑一遍即可。冗余列绝不允许成为唯一数据源。
-- ---------------------------------------------------------------------------


-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码（BCrypt，cost 10）',
    lineUserId   varchar(256)                           null comment 'LINE Login 用户 id（预留，暂未接入）',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',

    -- 冗余统计。[源] 各业务表 [同步] 增删时 +1/-1 [漂移] 个人中心数字偏大或偏小，不影响功能
    photoNum     int          default 0                 not null comment '照片数（冗余，源：photo）',
    albumNum     int          default 0                 not null comment '相册数（冗余，源：album_member）',
    friendNum    int          default 0                 not null comment '好友数（冗余，源：friend status=1）',
    capsuleNum   int          default 0                 not null comment '胶囊数（冗余，源：capsule）',

    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    unique key uk_userAccount (userAccount),
    index idx_lineUserId (lineUserId)
) comment '用户' collate = utf8mb4_unicode_ci;


-- 刷新令牌表（硬删除）
-- 访问令牌 15 分钟，刷新令牌 30 天。刷新时轮换：用掉的置 revokeTime，
-- 发新的一条并继承 familyId。已失效的令牌又被使用 = 判定盗用，整个 familyId 全失效。
create table if not exists refresh_token
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                             not null comment '用户 id',
    tokenHash  char(64)                           not null comment '令牌的 SHA-256，不存明文',
    familyId   varchar(64)                        not null comment '轮换系列 id，盗用检测时整族失效',
    expireTime datetime                           not null comment '过期时间',
    revokeTime datetime                           null comment '失效时间，非空表示已失效',
    userAgent  varchar(512)                       null comment '设备标识，用于「登出其他设备」',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_tokenHash (tokenHash),
    index idx_userId (userId),
    index idx_familyId (familyId)
) comment '刷新令牌' collate = utf8mb4_unicode_ci;


-- 好友关系表（硬删除）
-- 通过后写入双向两行，查好友列表只需 where userId = ? and status = 1，单列查询不 join
-- 待通过阶段只有 申请人 -> 目标 这一行
create table if not exists friend
(
    id               bigint auto_increment comment 'id' primary key,
    userId           bigint                             not null comment '用户 id',
    friendId         bigint                             not null comment '好友 id',
    status           tinyint  default 0                 not null comment '状态：0-待通过 1-已通过',
    requestUserId    bigint                             not null comment '发起申请的用户 id',

    -- 冗余对方展示信息。[源] user [同步] 跟随：user 改名/换头像时更新其全部 friend 行
    -- [漂移] 好友列表显示旧昵称，重算即可修复
    friendUserName   varchar(256)                       null comment '好友昵称（冗余，源：user.userName）',
    friendUserAvatar varchar(1024)                      null comment '好友头像（冗余，源：user.userAvatar）',

    createTime       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime       datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_userId_friendId (userId, friendId),
    index idx_userId_status (userId, status),
    index idx_friendId (friendId)
) comment '好友关系' collate = utf8mb4_unicode_ci;


-- 拉黑表（硬删除，单向）
-- 与 friend 不同，拉黑不对称：A 拉黑 B 时 B 不知道。
-- 拉黑后需同时删除双向好友关系，并在「用户搜索 / 好友申请 / 相册邀请」三处过滤。
create table if not exists block
(
    id              bigint auto_increment comment 'id' primary key,
    userId          bigint                             not null comment '发起拉黑的用户 id',
    blockedUserId   bigint                             not null comment '被拉黑的用户 id',
    blockedUserName varchar(256)                       null comment '被拉黑者昵称（冗余，源：user.userName，快照）',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_userId_blockedUserId (userId, blockedUserId),
    index idx_userId (userId)
) comment '拉黑关系' collate = utf8mb4_unicode_ci;


-- 照片表
-- 一张照片可以出现在多个相册里，所以这里只放照片本身的属性，
-- 「这张照片在这个相册里的说明 / 涂鸦」放 album_photo
create table if not exists photo
(
    id              bigint auto_increment comment 'id' primary key,
    userId          bigint                             not null comment '上传用户 id',
    userName        varchar(256)                       null comment '上传者昵称（冗余，源：user.userName，跟随）',

    picName         varchar(512)                       null comment '原始文件名',
    filePath        varchar(1024)                      not null comment '原图存储路径（已转正、已剥 EXIF）',
    thumbPath       varchar(1024)                      null comment '缩略图存储路径（400px）',
    picWidth        int                                null comment '图片宽度（旋转校正后）',
    picHeight       int                                null comment '图片高度（旋转校正后）',
    picScale        double                             null comment '宽高比 = picWidth / picHeight（冗余，避免前端布局时再算）',
    picSize         bigint                             null comment '图片体积（字节）',
    picFormat       varchar(32)                        null comment '图片格式：jpeg/png',
    sha256          char(64)                           null comment '原图内容哈希，同一用户同哈希视为重复上传',

    takenTime       datetime                           null comment '拍摄时间（EXIF DateTimeOriginal，取不到回落为上传时间）',
    takenTimeSource varchar(32)  default 'UPLOAD'      not null comment '拍摄时间来源：EXIF / UPLOAD。为 UPLOAD 时「時間」行需标注为推测值',
    latitude        decimal(10, 7)                     null comment '纬度（EXIF GPS）',
    longitude       decimal(10, 7)                     null comment '经度（EXIF GPS）',
    exifOrientation tinyint                            null comment '原始 EXIF 方向值 1-8，仅留档。像素已在入库时物理旋转，下游不得再依赖此值',

    -- 冗余统计。[源] album_photo [同步] 加入/移出相册时 +1/-1
    -- [漂移] 「未成册」筛选可能漏掉或多出照片
    albumNum        int          default 0             not null comment '被多少个相册引用（冗余，源：album_photo）。=0 即「未成册」',

    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_userId_takenTime (userId, takenTime),
    index idx_userId_albumNum (userId, albumNum),
    index idx_userId_sha256 (userId, sha256)
) comment '照片' collate = utf8mb4_unicode_ci;


-- 相册表
create table if not exists album
(
    id            bigint auto_increment comment 'id' primary key,
    title         varchar(512)                       null comment '相册标题（AI 生成，可改）',
    summary       varchar(1024)                      null comment '一句话总结（AI 生成）',
    albumDate     date                               null comment '相册日期（取自照片簇最早一张）',
    place         varchar(256)                       null comment '地点（取自照片簇）',

    coverPhotoId  bigint                             null comment '封面照片 id（AI 挑选，可改）',
    -- 冗余封面图。[源] photo [同步] 跟随：换封面时更新
    -- [漂移] 列表页显示旧封面 —— 但省掉了相册列表对 photo 的 join，这是最高频的查询
    coverPhotoUrl varchar(1024)                      null comment '封面原图路径（冗余，源：photo.filePath）',
    coverThumbUrl varchar(1024)                      null comment '封面缩略图路径（冗余，源：photo.thumbPath）',

    aiGenerated   tinyint  default 0                 not null comment '文案是否由 AI 生成：0-规则兜底 1-AI 生成',
    aiModel       varchar(64)                        null comment '生成所用模型，如 claude-opus-4-8。留档用，便于复盘',

    version       int      default 0                 not null comment '乐观锁版本号。作为 ETag 返回，更新时 If-Match 不一致则 409',

    userId        bigint                             not null comment '创建用户 id',
    userName      varchar(256)                       null comment '创建者昵称（冗余，源：user.userName，跟随）',

    -- 冗余统计。[源] album_photo / album_member / album_share
    photoNum      int      default 0                 not null comment '照片数（冗余，源：album_photo）',
    memberNum     int      default 1                 not null comment '成员数（冗余，源：album_member）',
    viewNum       int      default 0                 not null comment '公开页浏览数（冗余，源：album_share.viewNum）',

    -- 冗余分享状态。[源] album_share [同步] 跟随：创建分享时写入，撤销时置 null
    -- [漂移] 详情页分享按钮状态不对。这样相册详情页完全不 join album_share
    shareToken    varchar(64)                        null comment '当前有效的分享 token（冗余，源：album_share.shareToken）。null 表示未分享',

    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_userId_albumDate (userId, albumDate)
) comment '相册' collate = utf8mb4_unicode_ci;


-- 相册成员表（硬删除）
create table if not exists album_member
(
    id         bigint auto_increment comment 'id' primary key,
    albumId    bigint                                not null comment '相册 id',
    userId     bigint                                not null comment '用户 id',
    memberRole varchar(64) default 'editor'          not null comment '成员角色：owner/editor',

    -- 冗余。[源] user / album [同步] 跟随
    -- [漂移] 成员头像是旧的。省掉「我的相册」列表对 album 的 join
    userName   varchar(256)                          null comment '成员昵称（冗余，源：user.userName）',
    userAvatar varchar(1024)                         null comment '成员头像（冗余，源：user.userAvatar）',
    albumTitle varchar(512)                          null comment '相册标题（冗余，源：album.title）',

    createTime datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_albumId_userId (albumId, userId),
    index idx_albumId (albumId),
    index idx_userId (userId)
) comment '相册成员' collate = utf8mb4_unicode_ci;


-- 相册照片表
-- 关键：说明文字、排序、涂鸦层属于「这张照片在这个相册里」，不属于照片本身。
-- 同一张照片在两个相册里画不同的涂鸦是正常需求，所以这张表不是纯关联表。
-- 画面 8 的六行元数据（場所/時間/音楽/コメント/メンバー/天気）除了メンバー，都落在这里。
--
-- 本表冗余最重：渲染一个相册 = 查 album + 查 album_photo，两条 SQL，零 join。
create table if not exists album_photo
(
    id                bigint auto_increment comment 'id' primary key,
    albumId           bigint                             not null comment '相册 id',
    photoId           bigint                             not null comment '照片 id',
    position          int      default 0                 not null comment '在相册内的排序',

    -- 冗余照片展示字段。[源] photo [同步] 快照：加入相册时复制一次
    -- 原图路径入库后不再变化，所以快照是安全的。[漂移] 照片被删时这里仍指向旧路径
    photoUrl          varchar(1024)                      null comment '原图路径（冗余，源：photo.filePath）',
    thumbUrl          varchar(1024)                      null comment '缩略图路径（冗余，源：photo.thumbPath）',
    picWidth          int                                null comment '宽（冗余，源：photo.picWidth）',
    picHeight         int                                null comment '高（冗余，源：photo.picHeight）',
    picScale          double                             null comment '宽高比（冗余，源：photo.picScale）',
    takenTime         datetime                           null comment '拍摄时间（冗余，源：photo.takenTime）。画面 8「時間」行',

    -- 本表自有：AI 生成或用户填写
    caption           varchar(256)                       null comment '照片下方短标签，如「放課後」（AI 生成，≤10 字）',
    place             varchar(256)                       null comment '場所（GPS 逆地理，取不到则 AI 看图推断）',
    weather           varchar(64)                        null comment '天気：晴れ/曇り/雨/雪（AI 看图判断），其它值一律存 null',
    photoComment      varchar(1024)                      null comment 'コメント（AI 生成，用户可改）',
    music             varchar(512)                       null comment '音楽（只能用户手填，无自动来源）',

    -- 涂鸦。overlayData 是真实数据源，两个路径是它的渲染缓存
    overlayData       json                               null comment '涂鸦元素数据，真实数据源。stroke/text/sticker/filter，坐标归一化 0~1（手机画布 390px、OG 图 1200px，存绝对坐标必然错位）。只存 PNG 的话刷新后无法撤销单笔、无法再拖贴纸',
    overlayPath       varchar(1024)                      null comment '手写涂鸦层 PNG 路径（透明底，与原图同比例）。overlayData 的渲染缓存，丢了可重建',
    compositePath     varchar(1024)                      null comment '合成图路径（原图+涂鸦）。派生数据，overlayData 更新时失效。分享页和 OG 图用这个',
    overlayUserId     bigint                             null comment '最后画涂鸦的用户 id',
    overlayUserName   varchar(256)                       null comment '最后画涂鸦者昵称（冗余，源：user.userName，快照）',
    overlayUpdateTime datetime                           null comment '涂鸦最后更新时间。也用作 compositePath 的缓存失效参数 ?t=',

    version           int      default 0                 not null comment '乐观锁版本号。作为 ETag 返回，更新时 If-Match 不一致则 409',

    createTime        datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime        datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete          tinyint  default 0                 not null comment '是否删除',
    unique key uk_albumId_photoId (albumId, photoId),
    index idx_albumId_position (albumId, position),
    index idx_photoId (photoId)
) comment '相册照片' collate = utf8mb4_unicode_ci;


-- 相册分享表（硬删除）
-- shareToken 必须随机不可猜，不能用自增 id —— 分享页是公开的，能猜到 id 就能翻别人的相册。
--
-- 本表冗余最彻底：公开分享页 /s/{token} 和 OG 图 /og/{token}.jpg 只查这一张表，
-- 一次都不 join。理由是这两个接口要被 LINE 爬虫打，必须快且不能泄露内部字段。
create table if not exists album_share
(
    id            bigint auto_increment comment 'id' primary key,
    shareToken    varchar(64)                        not null comment '分享 token（SecureRandom 24 字节 → base64url 32 位）',
    albumId       bigint                             not null comment '相册 id',
    userId        bigint                             not null comment '创建分享的用户 id',

    -- 冗余快照。[源] album [同步] 快照：创建分享时复制，相册后续改名不跟
    -- [漂移] 分享出去的卡片显示当时的标题 —— 这是想要的行为，已发出的链接内容不应突变
    albumTitle    varchar(512)                       null comment '相册标题（冗余快照，源：album.title）。og:title',
    albumSummary  varchar(1024)                      null comment '相册总结（冗余快照，源：album.summary）。og:description',
    albumDate     date                               null comment '相册日期（冗余快照，源：album.albumDate）',
    coverPhotoUrl varchar(1024)                      null comment '封面路径（冗余快照，源：album.coverPhotoUrl）',
    photoNum      int      default 0                 not null comment '照片数（冗余快照，源：album.photoNum）',
    ogImagePath   varchar(1024)                      null comment 'OG 拼图 1200x630 的路径。首次被爬时生成并缓存',

    viewNum       int      default 0                 not null comment '浏览次数',
    expireTime    datetime                           null comment '过期时间，null 表示永久',
    revokeTime    datetime                           null comment '撤销时间，非空表示已撤销（访问返回 410）',

    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_shareToken (shareToken),
    index idx_albumId (albumId),
    index idx_userId (userId)
) comment '相册分享' collate = utf8mb4_unicode_ci;


-- 时间胶囊表
-- openTime 之前，接口不得返回 capsuleMsg 和相册内容。这个判断必须在服务端做，
-- 前端藏起来不算实现了这个功能（F12 一开就穿帮）。
create table if not exists capsule
(
    id            bigint auto_increment comment 'id' primary key,
    albumId       bigint                             not null comment '相册 id',
    userId        bigint                             not null comment '创建用户 id',

    -- 冗余快照。[源] album [同步] 快照：封存时复制
    -- 封存的是「当时的那个相册」，之后相册改名不应影响已封存的胶囊
    albumTitle    varchar(512)                       null comment '相册标题（冗余快照，源：album.title）',
    coverPhotoUrl varchar(1024)                      null comment '封面路径（冗余快照，源：album.coverPhotoUrl）。封存中仅用于生成模糊图',
    photoNum      int      default 0                 not null comment '照片数（冗余快照，源：album.photoNum）',

    capsuleMsg    varchar(2048)                      null comment '给未来的留言。封存期间绝不下发',
    openTime      datetime                           not null comment '可开启时间',
    openedTime    datetime                           null comment '实际开启时间，非空表示已开启',
    recipientNum  int      default 0                 not null comment '接收人数（冗余，源：capsule_recipient）。0 表示只给自己',

    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_albumId (albumId),
    index idx_openTime (openTime)
) comment '时间胶囊' collate = utf8mb4_unicode_ci;


-- 时间胶囊接收人表（硬删除）
-- 无记录时表示「只给创建者自己」，即海报原文的「1年後の自分へ」
create table if not exists capsule_recipient
(
    id         bigint auto_increment comment 'id' primary key,
    capsuleId  bigint                             not null comment '胶囊 id',
    userId     bigint                             not null comment '接收用户 id',
    userName   varchar(256)                       null comment '接收者昵称（冗余，源：user.userName，快照）',
    userAvatar varchar(1024)                      null comment '接收者头像（冗余，源：user.userAvatar，快照）',
    notifyTime datetime                           null comment '通知时间，非空表示已通知（防重复推送）',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_capsuleId_userId (capsuleId, userId),
    index idx_capsuleId (capsuleId),
    index idx_userId (userId)
) comment '时间胶囊接收人' collate = utf8mb4_unicode_ci;


-- 成册任务表（硬删除）
-- AI 成册是异步的，且一次请求可能产出多个相册（30 张照片横跨两个下午 = 两个相册），
-- 所以客户端轮询的是任务而不是相册
create table if not exists album_job
(
    id             bigint auto_increment comment 'id' primary key,
    userId         bigint                             not null comment '发起用户 id',

    photoIds       varchar(4096)                      not null comment '入参照片 id 列表（json 数组）',
    photoNum       int      default 0                 not null comment '入参照片数（冗余，源：photoIds 长度）。列表页不解析 json',
    albumIds       varchar(1024)                      null comment '产出的相册 id 列表（json 数组）',
    albumNum       int      default 0                 not null comment '产出相册数（冗余，源：albumIds 长度）',

    status         varchar(32)                        not null comment '状态：PENDING/CLUSTERING/ENRICHING/READY/FAILED/CANCELLED',
    progress       int      default 0                 not null comment '进度 0-100。ENRICHING 阶段按簇推进，前端据此显示进度而不是干转圈',
    aiGenerated    tinyint  default 0                 not null comment '本次是否真的用上了 AI：0-降级为规则兜底 1-AI 成功。AI 挂了仍然 READY，靠这个字段区分',

    errorCode      varchar(64)                        null comment '失败码，仅 FAILED 时有值',
    errorMsg       varchar(1024)                      null comment '失败原因（内部留档，不直接下发给前端）',

    idempotencyKey varchar(64)                        null comment '幂等键。会场 Wi-Fi 抖动导致前端重发时，同键直接返回原任务，避免 Claude 二次执行（二重课金 + 重复相册）',

    startTime      datetime                           null comment '实际开始执行时间（进队列到被取走有延迟）',
    finishTime     datetime                           null comment '完成时间',
    costMs         bigint                             null comment '耗时毫秒 = finishTime - startTime（冗余）。演示时可以说「20 张 18 秒」',

    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_userId_idempotencyKey (userId, idempotencyKey),
    index idx_userId_status (userId, status),
    index idx_userId_createTime (userId, createTime)
) comment '成册任务' collate = utf8mb4_unicode_ci;


-- 通知表（硬删除）
-- payload 用 json 存各类型自己的字段，不为每种通知加列 —— 类型会一直增加，加列会一直改表。
-- 但展示必需的字段（标题、正文、来源用户）仍然冗余成列，保证通知列表零 join。
create table if not exists notification
(
    id             bigint auto_increment comment 'id' primary key,
    userId         bigint                             not null comment '接收用户 id',
    notifyType     varchar(64)                        not null comment '类型：FRIEND_REQUEST/FRIEND_ACCEPTED/ALBUM_INVITED/ALBUM_UPDATED/CAPSULE_OPENABLE/SHARE_VIEWED',

    -- 冗余展示字段。[源] 各业务表 [同步] 快照：产生通知时写死
    -- 通知是「当时发生的事」，事后源数据变了也不应改写历史
    title          varchar(512)                       null comment '通知标题（快照，产生时写死）',
    content        varchar(1024)                      null comment '通知正文（快照）',
    fromUserId     bigint                             null comment '触发用户 id',
    fromUserName   varchar(256)                       null comment '触发用户昵称（冗余快照，源：user.userName）',
    fromUserAvatar varchar(1024)                      null comment '触发用户头像（冗余快照，源：user.userAvatar）',

    targetType     varchar(64)                        null comment '跳转目标类型：ALBUM/CAPSULE/FRIEND',
    targetId       bigint                             null comment '跳转目标 id',
    payload        json                               null comment '类型相关的附加数据',

    readTime       datetime                           null comment '已读时间，非空表示已读',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_userId_readTime (userId, readTime),
    index idx_userId_createTime (userId, createTime)
) comment '通知' collate = utf8mb4_unicode_ci;
