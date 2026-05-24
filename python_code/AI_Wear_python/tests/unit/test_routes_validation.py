from io import BytesIO


def test_validate_image_rejects_non_image_file(client):
    response = client.post(
        "/api/validate-image",
        data={"file": (BytesIO(b"this is not an image"), "fake.jpg")},
        content_type="multipart/form-data",
    )
    payload = response.get_json()

    assert response.status_code == 200
    assert payload == {"code": 200, "allow": False}


# 验证 /api/upload-image 在 JSON 请求缺少 ossUrl 时返回 400 校验失败。
def test_upload_image_requires_oss_url_for_json_request(client):
    response = client.post("/api/upload-image", json={"userId": "11"})
    payload = response.get_json()

    assert response.status_code == 400
    assert payload["code"] == 400
    assert payload["data"] is None


# 验证 /api/upload-image 在表单请求缺少 userId 时返回 400 校验失败。
def test_upload_image_requires_user_id_for_form_request(client):
    response = client.post(
        "/api/upload-image",
        data={"ossUrl": "https://oss.example.com/look.png"},
    )
    payload = response.get_json()

    assert response.status_code == 400
    assert payload["code"] == 400
    assert payload["data"] is None


# 验证 /api/search-image 在缺少 userId 时拒绝请求并返回 400。
def test_search_image_requires_user_id(client):
    response = client.post("/api/search-image", data={"query": "red dress"})
    payload = response.get_json()

    assert response.status_code == 400
    assert payload["code"] == 400
    assert payload["data"] is None


# 验证 /api/search-image 在 query 和 file 同时为空时返回 400。
def test_search_image_requires_query_or_file(client):
    response = client.post("/api/search-image", data={"userId": "3"})
    payload = response.get_json()

    assert response.status_code == 400
    assert payload["code"] == 400
    assert payload["data"] is None


# 验证 /api/search-image 文搜图分支会按 similarity 从高到低排序返回结果。
def test_search_image_text_query_returns_results_sorted_by_similarity(
    client, server_module, monkeypatch
):
    monkeypatch.setattr(
        server_module,
        "load_user_image_records",
        lambda user_id: [
            {"ossUrl": "https://img.example.com/c.png", "description": "blue jeans"},
            {"ossUrl": "https://img.example.com/a.png", "description": "red dress"},
            {"ossUrl": "https://img.example.com/b.png", "description": "red dress with belt"},
        ],
    )

    response = client.post(
        "/api/search-image",
        data={"userId": "7", "query": "red dress"},
    )
    payload = response.get_json()

    assert response.status_code == 200
    assert payload["code"] == 200
    assert [item["filePath"] for item in payload["data"]] == [
        "https://img.example.com/a.png",
        "https://img.example.com/b.png",
        "https://img.example.com/c.png",
    ]
    similarities = [item["similarity"] for item in payload["data"]]
    assert similarities == sorted(similarities, reverse=True)


# 验证 /api/search-image 图搜图分支只返回相似度达到 0.7 阈值以上的结果。
def test_search_image_file_query_filters_by_similarity_threshold(
    client, server_module, monkeypatch
):
    monkeypatch.setattr(
        server_module,
        "load_user_image_records",
        lambda user_id: [
            {"ossUrl": "https://img.example.com/a.png", "vector": [1.0, 0.0]},
            {"ossUrl": "https://img.example.com/b.png", "vector": "[0.8, 0.6]"},
            {"ossUrl": "https://img.example.com/c.png", "vector": [0.0, 1.0]},
        ],
    )
    monkeypatch.setattr(server_module, "image_to_clip_vector", lambda image_data: [1.0, 0.0])

    response = client.post(
        "/api/search-image",
        data={"userId": "7", "file": (BytesIO(b"fake-image"), "query.png")},
        content_type="multipart/form-data",
    )
    payload = response.get_json()

    assert response.status_code == 200
    assert payload["code"] == 200
    assert payload["data"] == [
        {"filePath": "https://img.example.com/a.png", "similarity": 1.0},
        {"filePath": "https://img.example.com/b.png", "similarity": 0.8},
    ]
