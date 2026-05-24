import importlib
import json
import sys
import types
from contextlib import contextmanager
from pathlib import Path

import pytest
'''
1.先计算项目根目录 PROJECT_ROOT
2.定义 _install_test_stubs()，专门伪造外部依赖
3.在 server_module fixture 中：
先安装 stub
再清理 server 缓存
最后重新导入 server.py
4.在 client fixture 中：
基于 server_module.app 创建 Flask 测试客户端

所以它本质上是在解决一个测试里的典型问题：
server.py 依赖很多外部服务和大模型，单元测试不能真的去调用它们，于是这里统一做了假实现替换。
'''

# Python 服务根目录。当前文件位于 tests/unit 下，向上两级就是 AI_Wear_python 目录。
PROJECT_ROOT = Path(__file__).resolve().parents[2]


def _install_test_stubs():
    # 确保 pytest 运行时可以直接 import server.py。
    if str(PROJECT_ROOT) not in sys.path:
        sys.path.insert(0, str(PROJECT_ROOT))

    # 伪造 dashscope 模块，避免导入 server.py 时触发真实云调用。
    dashscope_module = types.ModuleType("dashscope")
    dashscope_module.api_key = None

    class FakeMultiModalConversation:
        @staticmethod
        def call(**kwargs):
            # 返回与业务代码取值路径一致的固定响应结构。
            return {
                "output": {
                    "choices": [
                        {
                            "message": {
                                "content": [
                                    {"image": "http://example.com/mock-image.png"}
                                ]
                            }
                        }
                    ]
                }
            }

    dashscope_module.MultiModalConversation = FakeMultiModalConversation
    sys.modules["dashscope"] = dashscope_module

    # 伪造 deepagents 模块，避免创建真实 agent。
    deepagents_module = types.ModuleType("deepagents")

    class FakeAgent:
        def invoke(self, payload):
            # 模拟 agent 返回的最终消息结构，供 server.py 中的 invoke_agent 解析。
            return {
                "messages": [
                    types.SimpleNamespace(
                        content=json.dumps(
                            {
                                "success": True,
                                "url": "http://example.com/agent-output.png",
                            }
                        )
                    )
                ]
            }

    def create_deep_agent(*args, **kwargs):
        # 直接返回假 agent，阻止测试时初始化真实智能体。
        return FakeAgent()

    deepagents_module.create_deep_agent = create_deep_agent
    sys.modules["deepagents"] = deepagents_module

    # 伪造 torch 模块，只保留当前测试环境需要的最小接口。
    torch_module = types.ModuleType("torch")

    @contextmanager
    def no_grad():
        # 支持 with torch.no_grad(): 语法，但不做真实梯度控制。
        yield

    torch_module.no_grad = no_grad
    # 让业务代码走到 pooler_output 分支，而不是按真实 tensor 处理。
    torch_module.is_tensor = lambda value: False
    sys.modules["torch"] = torch_module

    # 伪造 transformers 模块，阻止测试时加载真实 CLIP 模型。
    transformers_module = types.ModuleType("transformers")

    class FakeCLIPModel:
        @classmethod
        def from_pretrained(cls, *args, **kwargs):
            # 模拟加载成功，直接返回假模型实例。
            return cls()

        def eval(self):
            # 与真实模型接口保持一致，但不执行任何逻辑。
            return None

        def get_image_features(self, **kwargs):
            # 返回 512 维假向量结构，满足 server.py 的维度校验。
            return types.SimpleNamespace(pooler_output=[[1.0] * 512])

    class FakeCLIPProcessor:
        @classmethod
        def from_pretrained(cls, *args, **kwargs):
            # 模拟加载成功，直接返回假处理器实例。
            return cls()

        def __call__(self, images=None, return_tensors=None):
            # 返回一个简单字典，模拟图片预处理后的输入结果。
            return {"images": images, "return_tensors": return_tensors}

    transformers_module.CLIPModel = FakeCLIPModel
    transformers_module.CLIPProcessor = FakeCLIPProcessor
    sys.modules["transformers"] = transformers_module

    # 伪造 langchain_community.chat_models 模块，避免真实大模型请求。
    langchain_community_module = types.ModuleType("langchain_community")
    chat_models_module = types.ModuleType("langchain_community.chat_models")

    class FakeChatTongyi:
        def __init__(self, *args, **kwargs):
            self.args = args
            self.kwargs = kwargs

        def invoke(self, payload):
            # describe_image 场景传入的是消息列表，这里返回图片描述结构。
            if isinstance(payload, list):
                return types.SimpleNamespace(content=[{"text": "mock description"}])
            # validate_image 等场景只需要一个可被业务代码识别的文本结果。
            return types.SimpleNamespace(content="是")

    chat_models_module.ChatTongyi = FakeChatTongyi
    langchain_community_module.chat_models = chat_models_module
    sys.modules["langchain_community"] = langchain_community_module
    sys.modules["langchain_community.chat_models"] = chat_models_module

    # 伪造 langchain_core 相关模块，满足 HumanMessage、Prompt、@tool 的导入需求。
    langchain_core_module = types.ModuleType("langchain_core")
    messages_module = types.ModuleType("langchain_core.messages")
    prompts_module = types.ModuleType("langchain_core.prompts")
    tools_module = types.ModuleType("langchain_core.tools")

    class HumanMessage:
        def __init__(self, content):
            self.content = content

    class ChatPromptTemplate:
        def __init__(self, messages):
            self.messages = messages

        @classmethod
        def from_messages(cls, messages):
            # 模拟根据消息列表构建 prompt 模板对象。
            return cls(messages)

        def format_messages(self):
            # 直接返回原始消息列表，满足业务代码调用要求。
            return self.messages

    def tool(func):
        # 测试环境下让 @tool 成为无副作用装饰器。
        return func

    messages_module.HumanMessage = HumanMessage
    prompts_module.ChatPromptTemplate = ChatPromptTemplate
    tools_module.tool = tool
    langchain_core_module.messages = messages_module
    langchain_core_module.prompts = prompts_module
    langchain_core_module.tools = tools_module
    sys.modules["langchain_core"] = langchain_core_module
    sys.modules["langchain_core.messages"] = messages_module
    sys.modules["langchain_core.prompts"] = prompts_module
    sys.modules["langchain_core.tools"] = tools_module


@pytest.fixture
def server_module():
    # 先安装全部 stub，再导入 server，避免导入阶段触发真实外部依赖。
    _install_test_stubs()
    # 每次 fixture 执行时都移除旧的 server 模块缓存，保证重新导入。
    sys.modules.pop("server", None)
    # 清理 import 缓存，避免模块搜索结果被旧状态影响。
    importlib.invalidate_caches()
    # 返回安全导入后的 server 模块，供测试直接调用函数或 app。
    return importlib.import_module("server")


@pytest.fixture
def client(server_module):
    # 基于 Flask app 创建测试客户端，用于路由层单元测试。
    return server_module.app.test_client()
