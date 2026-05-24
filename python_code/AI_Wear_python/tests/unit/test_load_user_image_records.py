import json


class FakeRedis:
    def __init__(self, set_members=None, scan_values=None, kv_store=None):
        self.set_members = set_members or set()
        self.scan_values = scan_values or []
        self.kv_store = kv_store or {}
        self.scan_patterns = []

    def smembers(self, key):
        return self.set_members

    def scan_iter(self, pattern):
        self.scan_patterns.append(pattern)
        return iter(self.scan_values)

    def get(self, key):
        return self.kv_store.get(key)


# 验证 load_user_image_records 在索引集合存在时直接按索引 key 读取并补充 redisKey 字段。
def test_load_user_image_records_uses_index_set(server_module, monkeypatch):
    fake_redis = FakeRedis(
        set_members={"image:upload:8:1", "image:upload:8:2"},
        kv_store={
            "image:upload:8:1": json.dumps({"ossUrl": "url-1", "description": "dress"}),
            "image:upload:8:2": json.dumps({"ossUrl": "url-2", "description": "shirt"}),
        },
    )
    monkeypatch.setattr(server_module, "get_redis_client", lambda: fake_redis)

    records = sorted(
        server_module.load_user_image_records("8"),
        key=lambda item: item["redisKey"],
    )

    assert records == [
        {
            "ossUrl": "url-1",
            "description": "dress",
            "redisKey": "image:upload:8:1",
        },
        {
            "ossUrl": "url-2",
            "description": "shirt",
            "redisKey": "image:upload:8:2",
        },
    ]
    assert fake_redis.scan_patterns == []


# 验证 load_user_image_records 在索引集合缺失时会回退到 scan_iter 扫描历史 key。
def test_load_user_image_records_falls_back_to_scan_when_index_missing(server_module, monkeypatch):
    fake_redis = FakeRedis(
        set_members=set(),
        scan_values=["image:upload:9:1"],
        kv_store={
            "image:upload:9:1": json.dumps({"ossUrl": "url-9", "description": "coat"})
        },
    )
    monkeypatch.setattr(server_module, "get_redis_client", lambda: fake_redis)

    records = server_module.load_user_image_records("9")

    assert records == [
        {
            "ossUrl": "url-9",
            "description": "coat",
            "redisKey": "image:upload:9:1",
        }
    ]
    assert fake_redis.scan_patterns == ["image:upload:9:*"]


# 验证 load_user_image_records 会跳过空值和非法 JSON，避免脏数据污染返回结果。
def test_load_user_image_records_skips_empty_and_invalid_json(server_module, monkeypatch):
    fake_redis = FakeRedis(
        set_members={"image:upload:5:1", "image:upload:5:2", "image:upload:5:3"},
        kv_store={
            "image:upload:5:1": json.dumps({"ossUrl": "url-5", "description": "valid"}),
            "image:upload:5:2": "",
            "image:upload:5:3": "{bad json}",
        },
    )
    monkeypatch.setattr(server_module, "get_redis_client", lambda: fake_redis)

    records = server_module.load_user_image_records("5")

    assert records == [
        {
            "ossUrl": "url-5",
            "description": "valid",
            "redisKey": "image:upload:5:1",
        }
    ]
