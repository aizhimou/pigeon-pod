# BiliBili API 接口文档

## 文档版本

- **版本号**: v1.0
- **���新日期**: 2026-02-14
- **适用范围**: BiliBili 公开 Web API

---

## 概述

本文档描述了 BiliBili 平台的公开 API 接口，主要用于获取用户信息、视频详情、合集信息等数据。

### 基础信息

- **Base URL**: `https://api.bilibili.com`
- **协议**: HTTPS
- **请求方法**: GET
- **响应格式**: JSON
- **字符编码**: UTF-8
- **认证方式**: 无需认证（公开API）

### 通用请求头

所有请求必须包含以下 HTTP 请求头：

```http
Accept: application/json
Accept-Language: zh-CN,zh;q=0.9
Origin: https://www.bilibili.com
Referer: https://www.bilibili.com/
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0
```

> ⚠️ **重要**: 缺少上述请求头可能导致请求被拒绝。

### 通用响应格式

所有 API 响应均遵循以下基本结构：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

- `code`: 状态码，`0` 表示成功，非 `0` 表示错误
- `message`: 状态描述信息
- `data`: 响应数据主体

---

## API 接口列表

### 目录

1. [视频详情接口](#1-视频详情接口)
2. [用户信息接口](#2-用户信息接口)
3. [用户视频列表接口](#3-用户视频列表接口)
4. [Season 合集信息接口](#4-season-合集信息接口)
5. [Series 合集信息接口](#5-series-合集信息接口)
6. [Series 合集视频列表接口](#6-series-合集视频列表接口)

---

## 1. 视频详情接口

### 接口描述

获取指定视频的详细信息，包括标题、简介、时长、UP主信息等。

### 接口地址

```
GET /x/web-interface/view
```

### 请求参数

#### Query Parameters

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| bvid | string | 是 | 视频的 BV 号 | BV1KDskz1EHD |

### 请求示例

```http
GET /x/web-interface/view?bvid=BV1KDskz1EHD HTTP/1.1
Host: api.bilibili.com
Accept: application/json
Accept-Language: zh-CN,zh;q=0.9
Origin: https://www.bilibili.com
Referer: https://www.bilibili.com/
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36
```

### 响应参数

#### 响应体结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "aid": 123456789,
    "bvid": "BV1KDskz1EHD",
    "title": "视频标题",
    "desc": "视频简介内容",
    "pic": "https://i0.hdslb.com/bfs/archive/xxxxx.jpg",
    "pubdate": 1640000000,
    "duration": 300,
    "is_upower_exclusive": false,
    "owner": {
      "mid": 12345678,
      "name": "UP主昵称",
      "face": "https://i0.hdslb.com/bfs/face/xxxxx.jpg"
    },
    "stat": {
      "view": 100000
    }
  }
}
```

#### 字段说明

| 字段路径 | 类型 | 说明 |
|---------|------|------|
| code | integer | 状态码，0 表示成功 |
| message | string | 状态描述 |
| data.aid | integer | 视频的 AV 号 |
| data.bvid | string | 视频的 BV 号 |
| data.title | string | 视频标题 |
| data.desc | string | 视频简介 |
| data.pic | string | 视频封面图片 URL |
| data.pubdate | integer | 发布时间（Unix 时间戳） |
| data.duration | integer | 视频时长（秒） |
| data.is_upower_exclusive | boolean | 是否为充电专属视频 |
| data.owner.mid | integer | UP主的用户 ID |
| data.owner.name | string | UP主昵称 |
| data.owner.face | string | UP主头像 URL |
| data.stat.view | integer | 播放量 |

### 错误码

| code | 说明 |
|------|------|
| 0 | 成功 |
| -400 | 请求错误 |
| -404 | 视频不存在 |
| 62002 | 稿件不可见 |

---

## 2. 用户信息接口

### 接口描述

获取指定用户（UP主）的基本信息，包括昵称、头像、签名、粉丝数等。

### 接口地址

```
GET /x/web-interface/card
```

### 请求参数

#### Query Parameters

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| mid | string | 是 | 用户 ID（UP主 ID） | 1302298364 |

### 请求示例

```http
GET /x/web-interface/card?mid=1302298364 HTTP/1.1
Host: api.bilibili.com
Accept: application/json
Referer: https://www.bilibili.com/
```

### 响应参数

#### 响应体结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "card": {
      "mid": "1302298364",
      "name": "用户昵称",
      "face": "https://i0.hdslb.com/bfs/face/xxxxx.jpg",
      "sign": "个性签名",
      "fans": 50000,
      "level_info": {
        "current_level": 6
      },
      "official": {
        "title": "知名UP主"
      }
    },
    "space": {
      "viewcount": 1000000
    },
    "follower": 100
  }
}
```

#### 字段说明

| 字段路径 | 类型 | 说明 |
|---------|------|------|
| code | integer | 状态码，0 表示成功 |
| message | string | 状态描述 |
| data.card.mid | string | 用户 ID |
| data.card.name | string | 用户昵称 |
| data.card.face | string | 用户头像 URL |
| data.card.sign | string | 个性签名 |
| data.card.fans | integer | 粉丝数 |
| data.card.level_info.current_level | integer | 用户等级（0-6） |
| data.card.official.title | string | 认证信息 |
| data.space.viewcount | integer | 空间访问量 |
| data.follower | integer | 关注数 |

### 错误码

| code | 说明 |
|------|------|
| 0 | 成功 |
| -400 | 请求错误 |
| -404 | 用户不存在 |

---

## 3. 用户视频列表接口

### 接口描述

获取指定用户上传的视频列表，支持分页查询。

### 接口地址

```
GET /x/series/recArchivesByKeywords
```

### 请求参数

#### Query Parameters

| 参数名 | 类型 | 必填 | 说明 | 示例 | 默认值 |
|--------|------|------|------|------|--------|
| mid | string | 是 | 用户 ID | 1302298364 | - |
| pn | integer | 是 | 页码（从 1 开始） | 1 | 1 |
| ps | integer | 是 | 每页数量（最大 100，0 表示全部） | 20 | 20 |
| keywords | string | 否 | 搜索关键词（留空表示不过滤） | | - |

### 请求示例

```http
GET /x/series/recArchivesByKeywords?keywords=&mid=1302298364&pn=1&ps=20 HTTP/1.1
Host: api.bilibili.com
Accept: application/json
Referer: https://www.bilibili.com/
```

### 响应参数

#### 响应体结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "archives": [
      {
        "aid": 123456789,
        "bvid": "BV1xx411x7xx",
        "ctime": 1640000000,
        "duration": 600,
        "pic": "https://i0.hdslb.com/bfs/archive/xxxxx.jpg",
        "pubdate": 1640000000,
        "stat": {
          "view": 50000
        },
        "state": 0,
        "title": "视频标题"
      }
    ],
    "page": {
      "num": 1,
      "size": 20,
      "total": 150
    }
  }
}
```

#### 字段说明

| 字段路径 | 类型 | 说明 |
|---------|------|------|
| code | integer | 状态码，0 表示成功 |
| message | string | 状态描述 |
| data.archives | array | 视频列表数组 |
| data.archives[].aid | integer | 视频 AV 号 |
| data.archives[].bvid | string | 视频 BV 号 |
| data.archives[].ctime | integer | 创建时间（Unix 时间戳） |
| data.archives[].duration | integer | 视频时长（秒） |
| data.archives[].pic | string | 封面图片 URL |
| data.archives[].pubdate | integer | 发布时间（Unix 时间戳） |
| data.archives[].stat.view | integer | 播放量 |
| data.archives[].state | integer | 视频状态（0 正常） |
| data.archives[].title | string | 视频标题 |
| data.page.num | integer | 当前页码 |
| data.page.size | integer | 每页数量 |
| data.page.total | integer | 视频总数 |

### 错误码

| code | 说明 |
|------|------|
| 0 | 成功 |
| -400 | 请求错误 |
| -404 | 用户不存在 |

---

## 4. Season 合集信息接口

### 接口描述

获取 Season 类型合集的信息和视频列表，支持分页。

### 接口地址

```
GET /x/polymer/web-space/seasons_archives_list
```

### 请求参数

#### Query Parameters

| 参数名 | 类型 | 必填 | 说明 | 示例 | 默认值 |
|--------|------|------|------|------|--------|
| season_id | string | 是 | 合集 ID | 678635 | - |
| mid | string | 是 | UP主用户 ID | 7380321 | - |
| page_num | integer | 是 | 页码（从 1 开始） | 1 | 1 |
| page_size | integer | 是 | 每页数量（最大 100） | 50 | 30 |

### 请求示例

```http
GET /x/polymer/web-space/seasons_archives_list?season_id=678635&mid=7380321&page_num=1&page_size=50 HTTP/1.1
Host: api.bilibili.com
Accept: application/json
Referer: https://www.bilibili.com/
```

### 响应参数

#### 响应体结构

```json
{
  "code": 0,
  "message": "success",
  "ttl": 1,
  "data": {
    "aids": [123456789, 987654321],
    "archives": [
      {
        "aid": 123456789,
        "bvid": "BV1xx411x7xx",
        "ctime": 1640000000,
        "duration": 600,
        "pic": "https://i0.hdslb.com/bfs/archive/xxxxx.jpg",
        "pubdate": 1640000000,
        "stat": {
          "view": 50000
        },
        "state": 0,
        "title": "视频标题"
      }
    ],
    "meta": {
      "category": 1,
      "cover": "https://i0.hdslb.com/bfs/archive/xxxxx.jpg",
      "description": "合集简介",
      "mid": 7380321,
      "name": "合集名称",
      "ptime": 1640000000,
      "season_id": 678635,
      "total": 100
    },
    "page": {
      "page_num": 1,
      "page_size": 50,
      "total": 100
    }
  }
}
```

#### 字段说明

| 字段路径 | 类型 | 说明 |
|---------|------|------|
| code | integer | 状态码，0 表示成功 |
| message | string | 状态描述 |
| ttl | integer | 生存时间 |
| data.aids | array | 视频 AID 列表 |
| data.archives | array | 视频详情列表（结构同"用户视频列表"） |
| data.meta.category | integer | 分类 ID |
| data.meta.cover | string | 合集封面 URL |
| data.meta.description | string | 合集简介 |
| data.meta.mid | integer | UP主用户 ID |
| data.meta.name | string | 合集名称 |
| data.meta.ptime | integer | ���布时间（Unix 时间戳） |
| data.meta.season_id | integer | 合集 ID |
| data.meta.total | integer | 合集内视频总数 |
| data.page.page_num | integer | 当前页码 |
| data.page.page_size | integer | 每页数量 |
| data.page.total | integer | 视频总数 |

### 错误码

| code | 说明 |
|------|------|
| 0 | 成功 |
| -400 | 请求错误 |
| -404 | 合集不存在 |

---

## 5. Series 合集信息接口

### 接口描述

获取 Series 类型合集的元信息（不含视频列表）。

### 接口地址

```
GET /x/series/series
```

### 请求参数

#### Query Parameters

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| series_id | string | 是 | 合集 ID | 1067956 |

### 请求示例

```http
GET /x/series/series?series_id=1067956 HTTP/1.1
Host: api.bilibili.com
Accept: application/json
Referer: https://www.bilibili.com/
```

### 响应参数

#### 响应体结构

```json
{
  "code": 0,
  "message": "success",
  "ttl": 1,
  "data": {
    "meta": {
      "series_id": 1067956,
      "mid": 7458285,
      "name": "合集名称",
      "description": "合集简介",
      "keywords": ["关键词1", "关键词2"],
      "creator": "创建者昵称",
      "state": 0,
      "last_update_ts": 1640000000,
      "total": 50,
      "ctime": 1630000000,
      "mtime": 1640000000,
      "raw_keywords": "关键词1,关键词2",
      "category": 1
    },
    "recent_aids": [123456789, 987654321]
  }
}
```

#### 字段说明

| 字段路径 | 类型 | 说明 |
|---------|------|------|
| code | integer | 状态码，0 表示成功 |
| message | string | 状态描述 |
| ttl | integer | 生存时间 |
| data.meta.series_id | integer | 合集 ID |
| data.meta.mid | integer | UP主用户 ID |
| data.meta.name | string | 合集名称 |
| data.meta.description | string | 合集简介 |
| data.meta.keywords | array | 关键词数组 |
| data.meta.creator | string | 创建者昵称 |
| data.meta.state | integer | 状态（0 正常） |
| data.meta.last_update_ts | integer | 最后更新时间（Unix 时间戳） |
| data.meta.total | integer | 合集内视频总数 |
| data.meta.ctime | integer | 创建时间（Unix 时间戳） |
| data.meta.mtime | integer | 修改时间（Unix 时间戳） |
| data.meta.raw_keywords | string | 原始关键词字符串 |
| data.meta.category | integer | 分类 ID |
| data.recent_aids | array | 最近视频的 AID 列表 |

### 错误码

| code | 说明 |
|------|------|
| 0 | 成功 |
| -400 | 请求错误 |
| -404 | 合集不存在 |

---

## 6. Series 合集视频列表接口

### 接口描述

获取 Series 类型合集的视频列表，支持分页。

### 接口地址

```
GET /x/series/archives
```

### 请求参数

#### Query Parameters

| 参数名 | 类型 | 必填 | 说明 | 示例 | 默认值 |
|--------|------|------|------|------|--------|
| mid | string | 是 | UP主用户 ID | 7458285 | - |
| series_id | string | 是 | 合集 ID | 1067956 | - |
| ps | integer | 是 | 每页数量（0 表示全部，最大 100） | 20 | 20 |
| pn | integer | 是 | 页码（从 1 开始） | 1 | 1 |

### 请求示例

```http
GET /x/series/archives?mid=7458285&series_id=1067956&ps=20&pn=1 HTTP/1.1
Host: api.bilibili.com
Accept: application/json
Referer: https://www.bilibili.com/
```

### 响应参数

#### 响应体结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "archives": [
      {
        "aid": 123456789,
        "bvid": "BV1xx411x7xx",
        "ctime": 1640000000,
        "duration": 600,
        "pic": "https://i0.hdslb.com/bfs/archive/xxxxx.jpg",
        "pubdate": 1640000000,
        "stat": {
          "view": 50000
        },
        "state": 0,
        "title": "视频标题"
      }
    ],
    "page": {
      "num": 1,
      "size": 20,
      "count": 50
    }
  }
}
```

#### 字段说明

| 字段路径 | 类型 | 说明 |
|---------|------|------|
| code | integer | 状态码，0 表示成功 |
| message | string | 状态描述 |
| data.archives | array | 视频列表数组（结构同"用户视频列表"） |
| data.page.num | integer | 当前页码 |
| data.page.size | integer | 每页数量 |
| data.page.count | integer | 视频总数 |

### 错误码

| code | 说明 |
|------|------|
| 0 | 成功 |
| -400 | 请求错误 |
| -404 | 合集不存在 |

---

## 通用数据结构

### Archive 对象

视频基本信息对象，在多个接口中使用：

```json
{
  "aid": 123456789,
  "bvid": "BV1xx411x7xx",
  "ctime": 1640000000,
  "duration": 600,
  "pic": "https://i0.hdslb.com/bfs/archive/xxxxx.jpg",
  "pubdate": 1640000000,
  "stat": {
    "view": 50000
  },
  "state": 0,
  "title": "视频标题"
}
```

| 字段 | 类型 | 说明 |
|-----|------|------|
| aid | integer | 视频 AV 号 |
| bvid | string | 视频 BV 号 |
| ctime | integer | 创建时间（Unix 时间戳） |
| duration | integer | 视频时长（秒） |
| pic | string | 封面图片 URL |
| pubdate | integer | 发布时间（Unix 时间戳） |
| stat.view | integer | 播放量 |
| state | integer | 视频状态（0 正常） |
| title | string | 视频标题 |

---

## 错误码说明

### 通用错误码

| code | 说明 | 处理建议 |
|------|------|---------|
| 0 | 成功 | - |
| -400 | 请求错误 | 检查请求参数格式 |
| -403 | 访问权限不足 | 检查请求头设置 |
| -404 | 资源不存在 | 确认资源 ID 是否正确 |
| -500 | 服务器内部错误 | 稍后重试 |
| -509 | 请求过于频繁 | 降低请求频率 |
| 62002 | 稿件不可见 | 视频已被删除或设为私密 |

---

## 使用示例

### cURL 示例

#### 获取视频详情

```bash
curl -X GET "https://api.bilibili.com/x/web-interface/view?bvid=BV1KDskz1EHD" \
  -H "Accept: application/json" \
  -H "Referer: https://www.bilibili.com/" \
  -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
```

#### ��取用户视频列表

```bash
curl -X GET "https://api.bilibili.com/x/series/recArchivesByKeywords?keywords=&mid=1302298364&pn=1&ps=20" \
  -H "Accept: application/json" \
  -H "Referer: https://www.bilibili.com/" \
  -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
```


## 注意事项

### ⚠️ 重要提示

1. **请求头必填**: 必须包含完整的 User-Agent、Referer 等请求头，否则请求可能被拒绝
2. **无需认证**: 这些接口为公开 API，无需登录或 API Key
3. **分页限制**: 单次请求最多返回 100 条数据
4. **速率限制**: 请合理控制请求频率，避免被限流（建议间隔 ≥ 1秒）
5. **时间戳**: 所有时间字段均为 Unix 时间戳（秒级）
6. **特殊视频**: 充电专属视频（`is_upower_exclusive: true`）可能无法正常获取内容

### 💡 最佳实践

1. **错误处理**: 始终检查响应中的 `code` 字段
2. **重试机制**: 遇到 `-500` 或 `-509` 错误时应实现指数退避重试
3. **缓存策略**: 对用户信息等不常变动的数据建议实现缓存
4. **并发控制**: 批量请求时应控制并发数量，避免触发限流
5. **视频状态**: 使用前检查 `state` 字段确认视频可用性

---

## 常见问题

### Q1: 为什么请求返回 403 错误？

**A**: 可能是缺少必要的请求头。请确保包含 `User-Agent`、`Referer` 和 `Origin` 字段。

### Q2: 如何获取所有视频而不分页？

**A**: 在支持的接口中，将 `ps` 参数设置为 `0`（仅限部分接口，如用户视频列表和 Series 合集）。

### Q3: 时间戳如何转换为日期？

**A**: API 返回的时间戳为 Unix 时间戳（秒级），可使用语言内置函数转换：
- Python: `datetime.fromtimestamp(timestamp)`
- JavaScript: `new Date(timestamp * 1000)`
- Go: `time.Unix(timestamp, 0)`

### Q4: 如何区分 Season 和 Series 合集？

**A**: 
- Season: 播放列表 URL 包含 `?type=season`
- Series: 播放列表 URL 包含 `?type=series`
- 调用的 API 接口也不同

### Q5: 视频 BV 号和 AV 号有什么区别？

**A**: 
- BV 号: 新版视频 ID，格式如 `BV1xx411x7xx`，推荐使用
- AV 号: 旧版视频 ID，数字格式，仍可使用但建议迁移到 BV 号

---

## 更新日志

### v1.0 (2026-02-14)

- 初始版本发布
- 包含 6 个核心 API 接口
- 提供部分示例代码

---

## 联系方式

如有问题或建议，请通过以下方式联系：

- **项目地址**: [yangtfu/podsync](https://github.com/yangtfu/podsync)
- **问题反馈**: GitHub Issues

---

## 许可证

本文档基于 [yangtfu/podsync](https://github.com/yangtfu/podsync) 项目整理，仅用于技术交流和学习目的。

BiliBili 相关商标和 API 归属于上海宽娱数码科技有限公司所有。