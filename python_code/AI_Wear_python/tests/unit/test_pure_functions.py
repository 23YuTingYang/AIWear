import pytest


# 验证 parse_vector 能把列表中的不同数值类型统一转换成 float 列表。
def test_parse_vector_accepts_list_input(server_module):
    result = server_module.parse_vector([1, "2", 3.5])

    assert result == [1.0, 2.0, 3.5]


# 验证 parse_vector 能正确解析 Redis 中常见的 JSON 字符串向量。
def test_parse_vector_accepts_json_string(server_module):
    result = server_module.parse_vector("[1, 2, 3.5]")

    assert result == [1.0, 2.0, 3.5]


@pytest.mark.parametrize(
    "vector_value",
    [
        {"x": 1},
        "not-json",
        "[1, \"bad\"]",
    ],
)
# 验证 parse_vector 遇到非列表、非法 JSON 或不可转浮点值时返回空列表。
def test_parse_vector_returns_empty_list_for_invalid_input(server_module, vector_value):
    result = server_module.parse_vector(vector_value)

    assert result == []


# 验证 cosine_similarity 对完全相同的向量计算结果接近 1.0。
def test_cosine_similarity_returns_one_for_same_vector(server_module):
    result = server_module.cosine_similarity([1, 2, 3], [1, 2, 3])

    assert result == pytest.approx(1.0)


# 验证 cosine_similarity 对正交向量的相似度结果为 0。
def test_cosine_similarity_returns_zero_for_orthogonal_vectors(server_module):
    result = server_module.cosine_similarity([1, 0], [0, 1])

    assert result == pytest.approx(0.0)


# 验证 cosine_similarity 在向量维度不一致时直接返回 0。
def test_cosine_similarity_returns_zero_for_mismatched_dimensions(server_module):
    result = server_module.cosine_similarity([1, 2], [1, 2, 3])

    assert result == 0.0


# 验证 cosine_similarity 在任一向量为零向量时不会报错且返回 0。
def test_cosine_similarity_returns_zero_for_zero_vector(server_module):
    result = server_module.cosine_similarity([0, 0], [1, 2])

    assert result == 0.0


# 验证 text_similarity 会先做去空格和大小写归一化再计算文本相似度。
def test_text_similarity_normalizes_case_and_whitespace(server_module):
    result = server_module.text_similarity("  RED DRESS  ", "red dress")

    assert result == pytest.approx(1.0)


@pytest.mark.parametrize(
    ("query", "description"),
    [
        ("", "red dress"),
        ("red dress", ""),
    ],
)
# 验证 text_similarity 在查询词或描述为空时返回 0，避免无意义匹配。
def test_text_similarity_returns_zero_when_input_missing(server_module, query, description):
    result = server_module.text_similarity(query, description)

    assert result == 0.0


# 验证 text_similarity 对相近描述的评分高于无关描述。
def test_text_similarity_scores_similar_text_higher(server_module):
    similar = server_module.text_similarity("red dress", "red dress with belt")
    different = server_module.text_similarity("red dress", "blue jeans")

    assert similar > different


# 验证 build_search_result 生成结果时优先使用 ossUrl，并把相似度保留四位小数。
def test_build_search_result_prefers_oss_url(server_module):
    result = server_module.build_search_result(
        {
            "ossUrl": "https://oss.example.com/red.png",
            "filePath": "D:/local/red.png",
            "url": "https://backup.example.com/red.png",
        },
        0.98765,
    )

    assert result == {
        "filePath": "https://oss.example.com/red.png",
        "similarity": 0.9877,
    }


# 验证 build_search_result 在缺少 ossUrl 时会按 filePath 再 url 的顺序回退。
def test_build_search_result_falls_back_to_file_path_then_url(server_module):
    file_path_result = server_module.build_search_result(
        {"filePath": "D:/images/look.png", "url": "https://example.com/look.png"},
        0.5,
    )
    url_result = server_module.build_search_result(
        {"url": "https://example.com/only-url.png"},
        0.12344,
    )

    assert file_path_result == {
        "filePath": "D:/images/look.png",
        "similarity": 0.5,
    }
    assert url_result == {
        "filePath": "https://example.com/only-url.png",
        "similarity": 0.1234,
    }
