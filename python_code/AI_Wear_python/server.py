import base64
import json
import os
import requests
import tempfile
import uuid
from difflib import SequenceMatcher
from io import BytesIO

import redis
import torch
import dashscope
from PIL import Image
from dashscope import MultiModalConversation
from deepagents import create_deep_agent
from transformers import CLIPModel, CLIPProcessor

from dotenv import load_dotenv
from flask import Flask, request, jsonify
from langchain_community.chat_models import ChatTongyi
from langchain_core.messages import HumanMessage
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.tools import tool

# 创建Flask实例
app = Flask(__name__)

# 获取配置信息
load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

# 获取接口密钥
API_KEY = os.getenv('DASHSCOPE_API_KEY')
if API_KEY:
    # DashScope 原生 SDK 不会自动读取 ChatTongyi 的 dashscope_api_key 参数。
    dashscope.api_key = API_KEY


else:
    print("DASHSCOPE_API_KEY 未配置，图片编辑模型无法调用")

# Redis配置，和Java项目中的Redis配置保持一致
REDIS_HOST = os.getenv('REDIS_HOST', '127.0.0.1')
REDIS_PORT = int(os.getenv('REDIS_PORT', '6379'))
REDIS_DB = int(os.getenv('REDIS_DB', '0'))
REDIS_TIMEOUT = int(os.getenv('REDIS_TIMEOUT', '2'))

# 本地 CLIP 模型路径，用于把图片转成 512 维向量
CLIP_MODEL_PATH = os.getenv('CLIP_MODEL_PATH', r"/path/to/clip-vit-base-patch16")

# 全局 Redis 客户端和 CLIP 模型，使用懒加载，避免服务启动时就加载大模型
redis_client = None
clip_model = None
clip_processor = None

'''获取 Redis 客户端'''


def get_redis_client():
    global redis_client
    if redis_client is None:
        redis_client = redis.Redis(
            host=REDIS_HOST,
            port=REDIS_PORT,
            db=REDIS_DB,
            socket_timeout=REDIS_TIMEOUT,
            socket_connect_timeout=REDIS_TIMEOUT,
            decode_responses=True
        )
    return redis_client


'''获取 CLIP 模型和处理器'''


def get_clip_model():
    global clip_model, clip_processor
    if clip_model is None or clip_processor is None:
        clip_processor = CLIPProcessor.from_pretrained(CLIP_MODEL_PATH, local_files_only=True)
        clip_model = CLIPModel.from_pretrained(CLIP_MODEL_PATH, local_files_only=True)
        clip_model.eval()
    return clip_model, clip_processor


'''根据 OSS 地址下载图片二进制数据'''


def download_image_from_oss(oss_url: str) -> bytes:
    resp = requests.get(oss_url, timeout=20)
    resp.raise_for_status()
    # 返回值是图片的二进制内容。
    return resp.content


'''使用 CLIP 把图片二进制数据转成 512 维向量'''


def image_to_clip_vector(image_data: bytes) -> list:
    # 模型负责真正提取图片特征。
    # 处理器负责把普通图片转换成模型能理解的输入格式。
    # clip_processor 负责图片预处理
    # clip_model 负责提取特征
    model, processor = get_clip_model()
    image = Image.open(BytesIO(image_data)).convert("RGB")
    # 它会做 CLIP 模型要求的标准预处理，通常包括:
    # 调整图片尺寸、中心裁剪、转成张量、像素归一化、增加批次维度。
    inputs = processor(images=image, return_tensors="pt")   #把“人能看懂的图片对象”转换成“模型能吃的标准输入数据”
    with torch.no_grad():
        # 把图片输入模型，得到图片的语义向量
        image_features = model.get_image_features(**inputs)
        # 不同 Transformers 版本返回值不完全一致：
        # 有的版本直接返回张量，有的版本返回带池化结果的模型输出对象。
        if not torch.is_tensor(image_features):
            if hasattr(image_features, "pooler_output"):
                image_features = image_features.pooler_output
            else:
                raise RuntimeError(f"CLIP图片向量返回类型不支持: {type(image_features)}")
        # 归一化后便于后续用余弦相似度做文搜图/图搜图
        image_features = image_features / image_features.norm(p=2, dim=-1, keepdim=True)
    vector = image_features[0].detach().cpu().tolist()
    if len(vector) != 512:
        raise RuntimeError(f"CLIP图片向量维度错误，当前维度: {len(vector)}")
    return vector


def load_user_image_records(user_id) -> list:
    """
    从 Redis 读取当前用户上传过的图片详情。

    参数:
        user_id: 当前用户 ID，用于定位用户维度的图片索引集合。
    返回:
        list: 当前用户的图片详情列表，列表元素是 Redis 中保存的 JSON 对象。
    """
    redis_cli = get_redis_client()
    index_key = f"image:upload:index:{user_id}"
    redis_keys = redis_cli.smembers(index_key)
    if not redis_keys:
        # 兼容没有索引集合的历史数据，实际新数据优先使用 image:upload:index:{userId}
        redis_keys = list(redis_cli.scan_iter(f"image:upload:{user_id}:*"))

    records = []
    for redis_key in redis_keys:
        # 索引集合里保存的是图片详情 key，需要再取一次真正的图片 JSON。
        raw_value = redis_cli.get(redis_key)
        if not raw_value:
            continue
        try:
            item = json.loads(raw_value)
            item["redisKey"] = redis_key  #把当前 Redis key 也放进这个字典里，方便后续排查或使用
            records.append(item)
        except json.JSONDecodeError:
            continue
    return records


def parse_vector(vector_value) -> list:
    """
    把 Redis 中保存的向量统一转换成 float 列表。

    参数:
        vector_value: Redis 中读取到的向量，可能是 list，也可能是 JSON 字符串。
    返回:
        list: 可用于余弦相似度计算的 float 向量；解析失败时返回空列表。
    """
    try:
        if isinstance(vector_value, str):
            vector_value = json.loads(vector_value)
        if not isinstance(vector_value, list):
            return []
        return [float(x) for x in vector_value]
    except (TypeError, ValueError, json.JSONDecodeError):
        return []


def cosine_similarity(vector_a: list, vector_b: list) -> float:
    """
    计算两个向量的余弦相似度。

    参数:
        vector_a: 查询图片向量。
        vector_b: Redis 中保存的图片向量。
    返回:
        float: 相似度分数，向量为空或维度不一致时返回 0。
    """
    if not vector_a or not vector_b or len(vector_a) != len(vector_b):
        return 0.0
    dot = sum(a * b for a, b in zip(vector_a, vector_b))  #点积
    norm_a = sum(a * a for a in vector_a) ** 0.5    #是在算两个向量各自的长度。
    norm_b = sum(b * b for b in vector_b) ** 0.5
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)


def text_similarity(query: str, description: str) -> float:
    """
    用 query 和 Redis 缓存中的 description 做文本相似度匹配。

    参数:
        query: 用户输入的搜索文本。
        description: 上传图片时生成并缓存的图片描述。
    返回:
        float: 文本相似度分数。
    """
    query = (query or "").strip().lower()
    description = (description or "").strip().lower()
    if not query or not description:
        return 0.0
    # 只计算 query 与 Redis 缓存 description 的文本相似度，不对包含关系做特殊加分。
    return SequenceMatcher(None, query, description).ratio()


def build_search_result(item: dict, similarity: float) -> dict:
    """
    统一搜索结果返回字段。

    参数:
        item: Redis 中的图片详情对象。
        similarity: 当前图片与查询条件的相似度。
    返回:
        dict: 前端和 Java 服务需要的 filePath、similarity 字段。
    """
    file_path = item.get("ossUrl") or item.get("filePath") or item.get("url")
    return {
        "filePath": file_path,
        "similarity": round(float(similarity), 4),
    }


'''
1.定义审核图片的接口路由
# POST /api/validate-image
'''


@app.route('/api/validate-image', methods=['POST'])
def validate_image_api():
    try:
        # 1.获取上传的图片文件
        file = request.files['file']
        # 2.读取图片 (二进制数据) 原始数据
        image_data = file.read()
        # 伪装成 jpg 的文本文件不应继续走审核和向量化链路。
        if not is_valid_image_data(image_data):
            return jsonify({"code": 200, 'allow': False}), 200
        # 3.调用大模型生成图片的文字描述信息
        image_desc = describe_image(image_data)
        # 4.二次调用大模型 处理图片文字描述信息 ,返回是或否
        allow = validate_image(image_desc)
        # 返回值格式为：响应体、HTTP 状态码。
        return jsonify({"code": 200, 'allow': allow}), 200
    except Exception as e:
        print(f"执行审核图片操作出现异常!{e}")
        return jsonify({"code": 500, 'allow': False}), 500


'''将图片二进制数据转化为数据地址'''


def is_valid_image_data(image_data: bytes) -> bool:
    try:
        with Image.open(BytesIO(image_data)) as image:
            image.verify()
        return True
    except (OSError, ValueError):
        return False


def image_to_base_uri(image_data: bytes) -> str:
    image = Image.open(BytesIO(image_data))
    image_format = (image.format).lower()
    image_base64 = base64.b64encode(image_data).decode("utf-8")
    # 注意格式写法，格式为 data:image/png;base64,xxxx。
    data_uri = f"data:image/{image_format};base64,{image_base64}"
    return data_uri


'''定义大模型 生成 图片的文字描述信息'''


def describe_image(image_data: bytes) -> str:
    try:
        # 1. 把图片转为 base64 编码
        data_uri = image_to_base_uri(image_data)
        # 2. 构建大模型请求
        human_content = [
            {"image": data_uri},
            {
                "text": (
                    "用一句话简要概括这张图片的内容."
                    "并给出3到5个关键字(用逗号分割开)不要过多的解释"
                )
            },
        ]
        # 3.构建访问大模型的实例
        #qwen-vl-max
        qwen_vl_max_llm = ChatTongyi(
            model_name="qwen-vl-plus",
            temperature=0.0,
            dashscope_api_key=API_KEY
        )
        # 4.把访问大模型的结果进行返回
        resp = qwen_vl_max_llm.invoke([HumanMessage(content=human_content)])
        return resp.content[0]["text"]
    except Exception as e:
        print(f"执行生成图片描述信息操作出现异常!{e}")
        return ""


'''
定义大模型
对图片的描述信息做最终的判定 
'''


def validate_image(image_desc: str) -> bool:
    try:
        prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    "你是⼀个图⽚审核助⼿，当前的业务只允许两种图⽚："
                    "1) ⾐服/服装/穿搭相关;"
                    "2) ⼈物⼈像(⼈脸照、半⾝照、全⾝照).\n"
                    "需要你来判断当前图⽚的内容是否是以上两类图⽚，如果是输出是，如果否输出否。"
                    "请严格只输出是或者否，不要别的内容!"
                ),
                (
                    "human", f"图⽚⽂字描述: {image_desc}"
                )
            ]
        )
        qwen_plus_llm = ChatTongyi(
            model_name="qwen-plus-2025-09-11",
            temperature=0.7,
            dashscope_api_key=API_KEY
        )
        resp = qwen_plus_llm.invoke(prompt.format_messages())
        text = resp.content
        # 判断模型回复是不是以“是”开头。
        return text.startswith("是")
    except Exception as e:
        print(f"执行图片描述信息判定操作出现异常!{e}")
        return False
    pass


'''
2.定义上传图片到向量库的接口路由
# POST /api/upload-image
'''


# 接收一张已经上传到 OSS 的图片地址，把图片下载下来，生成图片描述，生成 CLIP 向量，
# 然后把图片信息和向量保存到 Redis，方便后续做“文搜图 / 图搜图”。
@app.route('/api/upload-image', methods=['POST'])
def upload_image_api():
    try:
        # 兼容 JSON 和表单两种请求方式，Java 后端调用时使用哪种都可以。
        data = request.get_json(silent=True) or request.form
        oss_url = data.get("ossUrl") or data.get("url") or data.get("image")
        user_id = data.get("userId")

        if not oss_url:
            return jsonify({"code": 400, "message": "oss地址不能为空", "data": None}), 400
        if user_id is None or str(user_id).strip() == "":
            return jsonify({"code": 400, "message": "userId不能为空", "data": None}), 400

        # 1. 根据 OSS 地址下载图片
        image_data = download_image_from_oss(oss_url)
        # 2. 调用 qwen-vl-max 生成图片描述信息
        image_desc = describe_image(image_data)
        # 3. 调用本地 CLIP 模型生成 512 维图片向量
        image_vector = image_to_clip_vector(image_data)

        # 4. 把用户ID、OSS地址、描述信息、向量组装成 JSON 保存到 Redis
        image_id = uuid.uuid4().hex
        redis_key = f"image:upload:{user_id}:{image_id}"
        redis_index_key = f"image:upload:index:{user_id}"
        redis_value = {
            "imageId": image_id,
            "userId": str(user_id),
            "ossUrl": oss_url,
            "description": image_desc,
            "vector": image_vector
        }
        redis_cli = get_redis_client()
        redis_cli.set(redis_key, json.dumps(redis_value, ensure_ascii=False))
        # 额外维护一个用户维度索引，后续文搜图/图搜图可以直接拿到该用户的图片key列表
        redis_cli.sadd(redis_index_key, redis_key)

        return jsonify(
            {
                "code": 200,
                "message": "上传图片向量保存成功",
                "data": {
                    "redisKey": redis_key,
                    "userId": str(user_id),
                    "ossUrl": oss_url,
                    "description": image_desc,
                    "vectorSize": len(image_vector)
                }
            }
        ), 200
    except Exception as e:
        print(f"上传图片并保存向量发生异常: {e}")
        return jsonify({"code": 500, "message": "上传图片向量保存失败", "data": None}), 500


# 3. 定义文搜图 / 图搜图的接口路由 POST /api/search-image

@app.route('/api/search-image', methods=['POST'])
def search_image_api():
    """
    检索相似图片，支持文搜图和图搜图。

    请求:
        multipart/form-data，userId 必填，query 和 file 二选一。
    返回:
        JSON: 按相似度从高到低排序的图片列表。
    """
    try:
        user_id = request.form.get("userId") or request.args.get("userId")
        query = (request.form.get("query") or request.args.get("query") or "").strip()
        file = request.files.get("file")

        if user_id is None or str(user_id).strip() == "":
            return jsonify({"code": 400, "message": "userId不能为空", "data": None}), 400
        if not query and (file is None or file.filename == ""):
            return jsonify({"code": 400, "message": "query和file不能同时为空", "data": None}), 400

        # 先按 userId 取出该用户上传过的所有图片缓存，再在内存中做相似度排序。
        image_records = load_user_image_records(user_id)
        results = []

        if file is not None and file.filename != "":
            # 图搜图：提取上传图片向量，与 Redis 中保存的图片向量做余弦相似度。
            query_vector = image_to_clip_vector(file.read()) # 提取上传图片向量
            for item in image_records:
                stored_vector = parse_vector(item.get("vector"))  #从 Redis 图片记录里取出已保存的图片向量。
                similarity = cosine_similarity(query_vector, stored_vector)  #计算查询图片向量和 Redis 图片向量的余弦相似度。
                if similarity >= 0.7:
                    #值越大，表示两张图片越相似。
                    results.append(build_search_result(item, similarity))
        else:
            # 文搜图：只用 query 和 Redis 缓存中的 description 做文本相似度匹配。
            for item in image_records:
                similarity = text_similarity(query, item.get("description"))
                if similarity >= 0.08:
                    #返回值是 0 到 1 之间的小数。越接近 1，说明越相似。
                    results.append(build_search_result(item, similarity))

        # 两种搜索方式都统一按 similarity 倒序返回。
        results.sort(key=lambda x: x["similarity"], reverse=True)
        return jsonify({"code": 200, "message": "查询成功", "data": results}), 200
    except Exception as e:
        #比如 Redis 连接失败、CLIP 模型加载失败、图片解析失败
        print(f"检索相似图片发生异常: {e}")
        return jsonify({"code": 500, "message": "查询失败", "data": None}), 500


# -----------------------------------------------------------------------------------------

# 全局智能体
deep_agent = None

# 定义大模型
llm = ChatTongyi(
    model_name="qwen-plus-2025-09-11",
    temperature=0.1,
    dashscope_api_key=API_KEY
)


@tool
def edit_image_tool(image_path: str, instruction: str) -> str:
    """编辑单张图⽚"
    输⼊本地图⽚地址路径与编辑指令 调⽤Dashscope的图像编辑模型⽣成新的URL
    返回JSON字符串: {"success": true, "url":"..."} 或 {"success": false,"error":"..."}
    """
    try:
        # 读取图片
        with open(image_path, "rb") as f:
            image_data = f.read()
        # 图片转为数据地址
        image_data_uri = image_to_base_uri(image_data)
        messages = [
            {
                "role": "user",
                "content": [
                    {
                        "image": image_data_uri
                    },
                    {
                        "text": instruction
                    }
                ]
            }
        ]
        # 构建请求大模型的参数
        params = {
            "model": "qwen-image-edit",
            "messages": messages,
        }
        # 调用 qwen-image-edit-plus，这是“图片编辑 / 图片生成类模型”。
        # 它的输入输出和聊天模型不一样：
        # 输入：图片 + 编辑指令
        # 输出：新图片地址或图片结果
        # 返回结构：更偏多模态生成结果，不是普通聊天文本
        # 所以代码使用 DashScope 原生接口。
        # 调用 DashScope 编辑模型
        response = MultiModalConversation.call(**params)

        url = response['output']['choices'][0]['message']['content'][0]['image']
        return json.dumps({"success": True, "url": url}, ensure_ascii=False)
    except Exception as e:
        print(f"调用编辑模型报错:{e}")
        return json.dumps({"success": False, "error": str(e)}, ensure_ascii=False)


@tool
def merge_image_tool(image_path1: str, image_path2: str, instruction: str) -> str:
    """合并两张图片"
    输入两张本地图片地址路径与合并指令 调用Dashscope的图像编辑模型生成新的URL
    返回JSON字符串: {"success": true, "url":"..."} 或 {"success": false,"error":"..."}
    """
    try:
        # 读取图片1
        with open(image_path1, "rb") as f:
            image_data1 = f.read()
        # 读取图片2
        with open(image_path2, "rb") as f:
            image_data2 = f.read()
        # 图片转为数据地址
        image_data_uri1 = image_to_base_uri(image_data1)
        image_data_uri2 = image_to_base_uri(image_data2)
        messages = [
            {
                "role": "user",
                "content": [
                    {
                        "image": image_data_uri1
                    },
                    {
                        "image": image_data_uri2
                    },
                    {
                        "text": instruction
                    }
                ]
            }
        ]
        # 构建请求大模型的参数
        params = {
            "model": "qwen-image-edit",
            "messages": messages,
        }
        # 调用 qwen-image-edit-plus，这里继续使用“图片编辑 / 图片生成类模型”。
        # 它的输入输出和聊天模型不一样：
        # 输入：图片1 + 图片2 + 合并指令
        # 输出：新图片地址或图片结果
        # 返回结构：更偏多模态生成结果，不是普通聊天文本
        # 所以代码使用 DashScope 原生接口。
        # 调用 DashScope 合并模型
        response = MultiModalConversation.call(**params)

        url = response['output']['choices'][0]['message']['content'][0]['image']
        return json.dumps({"success": True, "url": url}, ensure_ascii=False)
    except Exception as e:
        print(f"调用合并模型报错:{e}")
        return json.dumps({"success": False, "error": str(e)}, ensure_ascii=False)


# 声明一下工具
skill_tools = [edit_image_tool, merge_image_tool]

# 创建智能体
try:
    deep_agent = create_deep_agent(
        model=llm,
        tools=skill_tools,
        skills=["./skills"],  # 当前目录下的 skills 文件夹
        system_prompt=(
            "你是⼀个智能图⽚处理助⼿，你可以调⽤⼀个⼯具：\n"
            "- edit_image_tool：编辑单张图⽚ \n"
            "- merge_image_tool：合并两张图片 \n"
            "当用户提供 image_path1 和 image_path2 时，你需要根据 instruction 判断并优先调用 merge_image_tool。\n"
            "当用户只提供 image_path 时，你调用 edit_image_tool。\n"
            "最终的输出格式JSON: {\"success\":true, \"url\":\"字符串类型\"} 或者 {\"success\":false, \"error\":\"字符串类型\"} "
        ),
    )
    print("Agent 已经启用")
except Exception as e:
    print(f"启动Agent失败: {e}")
    deep_agent = None


# 智能体执行函数
# 把提示词发给智能体，让智能体判断并调用工具。
def invoke_agent(param: str) -> dict:
    try:
        # 返回的通常是一个状态字典。
        # 负责理解用户需求，决定调用哪个工具。
        state = deep_agent.invoke(
            {
                "messages": [
                    {
                        "role": "user",
                        "content": param,
                    }
                ]
            }
        )
        # 尝试从状态字典里面获取消息列表。
        msg = (state or {}).get("messages") or []
        # 这里为什么取最后一条:因为聊天模型执行过程中可能有多条消息：
        # 用户消息
        # 工具调用消息
        # 工具返回消息
        # 模型最终回复消息
        # 最后一条通常被认为是智能体的最终回复。
        last = msg[-1] if msg else None
        content = last.content
        # 把智能体输出的 JSON 字符串转成 Python 字典。
        obj = json.loads(content)
        return obj
    except Exception as e:
        print(f"执行Agent函数发生异常: {e}")
        return {"success": False, "error": str(e)}


# 操作图片的函数
# 读取上传文件和编辑指令，把图片保存成临时文件，拼接给智能体的提示词。
def skill_image() -> str:
    try:
        if deep_agent is None:
            return ""
            # 获取请求的参数
            # 接受指令
        instruction = request.form.get("instruction")
        if instruction is None:
            return ""
        # 接收图片 接收到图片之后 应该怎么处理
        # 对图片进行临时的保存
        tmp_dir = tempfile.gettempdir()  # 获取临时目录
        tmp_paths = []  # 临时文件列表
        # 构造给智能体的文本内容
        prompt_lines = [
            "你必须调⽤⼀个⼯具，并且只输出 JSON格式",
            f"instruction: {instruction}",
        ]
        # 两张图片合并
        if "file1" in request.files and "file2" in request.files:
            # 读取到图片1的二进制数据
            data1 = request.files["file1"].read()
            # 生成临时文件1
            p1 = os.path.join(tmp_dir, f"aiwear_{uuid.uuid4().hex}.bin")
            # 保存图片1（二进制写入模式）
            with open(p1, "wb") as f:
                f.write(data1)
            tmp_paths.append(p1)
            # 读取到图片2的二进制数据
            data2 = request.files["file2"].read()
            # 生成临时文件2
            p2 = os.path.join(tmp_dir, f"aiwear_{uuid.uuid4().hex}.bin")
            # 保存图片2（二进制写入模式）
            with open(p2, "wb") as f:
                f.write(data2)
            tmp_paths.append(p2)
            # 添加图片路径
            prompt_lines.append(f"image_path1: {p1}")
            prompt_lines.append(f"image_path2: {p2}")
            out = invoke_agent("\\n".join(prompt_lines))
            if not out.get("success") or not out.get("url"):
                raise RuntimeError(out.get("error") or "图片处理失败")
            return out["url"]  # 这里只要返回图片地址
        # 编辑图片的路径 读取到图片的二进制数据
        data = request.files["file"].read()
        # 生成临时文件
        p = os.path.join(tmp_dir, f"aiwear_{uuid.uuid4().hex}.bin")
        # 保存图片（二进制写入模式）
        with open(p, "wb") as f:
            f.write(data)
        tmp_paths.append(p)
        # 添加图片路径
        prompt_lines.append(f"image_path: {p}")
        out = invoke_agent("\n".join(prompt_lines))
        if not out.get("success") or not out.get("url"):
            raise RuntimeError(out.get("error") or "图片处理失败")
        return out["url"]  # 这里只要返回图片地址
    except Exception as e:
        print(f"操作图片,执行skill发生异常{e}")
        raise

'''2.定义操作图片 (编辑图片 + 合并图片) 的接口路由'''


@app.route('/api/skill/image', methods=['POST'])
def skill_image_api():
    try:
        out = skill_image()
        return jsonify(
            {
                "success": True,
                "url": out,
            }
        ), 200
    except Exception as e:
        print(f"调用skill处理失败{e}")
        return jsonify(
            {
                "success": False,
                "message": str(e),
            }
        ), 500


# 服务器启动函数
if __name__ == '__main__':
    print("AI服务启动成功!")
    app.run(debug=True, use_reloader=False, host='0.0.0.0', port=6789)
